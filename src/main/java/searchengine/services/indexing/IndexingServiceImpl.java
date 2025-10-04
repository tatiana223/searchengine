package searchengine.services.indexing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.exception.IndexingAlreadyStartedException;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class IndexingServiceImpl implements IndexingService{

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private PageCrawler pageCrawler;

    @Autowired
    private SitesList sitesList;

    private boolean indexingInProgress = false;

    @Override
    @Transactional
    public synchronized void startIndexing() throws IndexingAlreadyStartedException {
        if (indexingInProgress) {
            throw new IndexingAlreadyStartedException("Индексация уже запущена");
        }

        indexingInProgress = true;

        for (Site siteConfig : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);

            if (siteEntity == null) {
                siteEntity = new SiteEntity();
                siteEntity.setUrl(siteConfig.getUrl());
                siteEntity.setName(siteConfig.getName());
            }

            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(null);
            siteRepository.save(siteEntity);

            SiteEntity finalSite = siteEntity;

            // Каждый сайт индексируем в отдельном потоке
            new Thread(() -> {
                try {
                    pageCrawler.crawlSite(finalSite);
                } catch (Exception e) {
                    finalSite.setStatus(Status.FAILED);
                    finalSite.setStatusTime(LocalDateTime.now());
                    finalSite.setLastError(e.getMessage());
                    siteRepository.save(finalSite);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @Override
    @Transactional
    public synchronized void stopIndexing() {
        pageCrawler.stop();

        siteRepository.findAll()
                .stream()
                .filter(s -> s.getStatus() == Status.INDEXING)
                .forEach(site -> {
                    site.setStatus(Status.FAILED);
                    site.setStatusTime(LocalDateTime.now());
                    site.setLastError("Индексация остановлена пользователем");
                    siteRepository.save(site);
                });
        indexingInProgress = false;
    }

    @Override
    @Transactional
    public boolean indexPage(String url) {
        Site siteConfig = sitesList.getSites().stream()
                .filter(s -> {
                    try {

                        URI siteUri = new URI(s.getUrl());
                        URI inputUri = new URI(url);

                        // Сравниваем домены (игнорируя www и протокол)
                        String siteHost = siteUri.getHost().replace("www.", "");
                        String inputHost = inputUri.getHost().replace("www.", "");

                        return siteHost.equals(inputHost);
                    } catch (URISyntaxException e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);

        if (siteConfig == null) {
            System.out.println("Страница не принадлежит сайтам из application.yaml");
            return false;
        }
        System.out.println("Определён сайт: " + siteConfig.getName() + " (" + siteConfig.getUrl() + ")");

        SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    SiteEntity newSite = new SiteEntity();
                    newSite.setUrl(siteConfig.getUrl());
                    newSite.setName(siteConfig.getName());
                    newSite.setStatus(Status.INDEXING);
                    newSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(newSite);
                    System.out.println("Создан новый сайт в БД: " + newSite.getUrl());
                    return newSite;
                });
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new RuntimeException("Некорректный  URL: " + url);
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getQuery() != null) {
            path += "?" + uri.getQuery();
        }

        pageRepository.findBySiteAndPath(siteEntity, path).ifPresent(existingPage -> {
            System.out.println("Страница уже существует, удаляем старую версию: " + existingPage);
            var indexes = indexRepository.findAllByPage(existingPage);
            System.out.println("Удаляем индексов: " + indexes.size());
            for (var idx : indexes) {
                LemmaEntity lemma = idx.getLemma();
                lemma.setFrequency(lemma.getFrequency() - 1);
                lemmaRepository.save(lemma);
                indexRepository.delete(idx);
            }

            pageRepository.delete(existingPage);
            System.out.println("Страница уже существует в БД, удаляем: " + existingPage);
        });
        pageCrawler.crawlSinglePage(siteEntity, url);
        System.out.println("=== Индексация завершена: " + url);

        return true;
    }


}
