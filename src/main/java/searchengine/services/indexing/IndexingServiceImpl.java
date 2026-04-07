package searchengine.services.indexing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.exception.IndexingAlreadyStartedException;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingServiceImpl implements IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingServiceImpl.class);

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

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);


    @Transactional
    @Override
    public synchronized void startIndexing() throws IndexingAlreadyStartedException {
        if (!indexingInProgress.compareAndSet(false, true)) {
            throw new IndexingAlreadyStartedException("Индексация уже запущена");
        }

        pageCrawler.resetStopFlag();
        log.info("Запускаем индексацию для {} сайтов", sitesList.getSites().size());

        for (Site siteConfig : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElse(null);

            if (siteEntity != null) {

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
                    log.error("Ошибка при индексации сайта: {}", finalSite.getUrl(), e);
                    finalSite.setStatus(Status.FAILED);
                    finalSite.setStatusTime(LocalDateTime.now());
                    finalSite.setLastError(e.getMessage());
                    siteRepository.save(finalSite);
                }
            }).start();
        }
    }

    @Override
    public synchronized void stopIndexing() {
        log.info("Остановка индексации по запросу пользователя");
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
        indexingInProgress.set(false);
    }

    @Override
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
            log.warn("Страница {} не принадлежит ни одному сайту из конфигурации", url);
            return false;
        }

        SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl())
                .orElse(null);

        if (siteEntity == null) {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setName(siteConfig.getName());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity = siteRepository.save(siteEntity);
        }

        try {
            pageCrawler.indexSinglePageOnly(siteEntity, url);
            log.info("Индексация страницы успешно завершена: {}", url);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }
}