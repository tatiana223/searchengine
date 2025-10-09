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
            System.out.println("Блокировка: indexingInProgress = true");
            throw new IndexingAlreadyStartedException("Индексация уже запущена");
        }

        indexingInProgress = true;

        pageCrawler.resetStopFlag();
        System.out.println("Запускаем индексацию для " + sitesList.getSites().size() + " сайтов");

        for (Site siteConfig : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);

            if (siteEntity != null) {

                Integer siteId = siteEntity.getId();
                indexRepository.deleteBySite(siteId);
                lemmaRepository.deleteBySite(siteId);
                pageRepository.deleteBySite(siteId);

                siteEntity.setStatus(Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteEntity.setLastError(null);
                siteRepository.save(siteEntity);
            } else {

                siteEntity = new SiteEntity();
                siteEntity.setUrl(siteConfig.getUrl());
                siteEntity.setName(siteConfig.getName());
                siteEntity.setStatus(Status.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteEntity.setLastError(null);
                siteRepository.save(siteEntity);
            }


            SiteEntity finalSite = siteEntity;

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

        SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    SiteEntity newSite = new SiteEntity();
                    newSite.setUrl(siteConfig.getUrl());
                    newSite.setName(siteConfig.getName());
                    newSite.setStatus(Status.INDEXED);
                    newSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(newSite);
                    return newSite;
                });

        try {
            pageCrawler.indexSinglePageOnly(siteEntity, url);

            System.out.println("=== Индексация одной страницы завершена: " + url);
            return true;

        } catch (Exception e) {
            System.err.println("Ошибка при индексации страницы " + url + ": " + e.getMessage());
            return false;
        }
    }


}
