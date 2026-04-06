package searchengine.services.indexing;

import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repository.*;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
public class PageCrawler {
    private volatile boolean stopRequested = false;

    @Autowired private PageRepository pageRepository;
    @Autowired private LemmaRepository lemmaRepository;
    @Autowired private IndexRepository indexRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private LemmaService lemmaService;

    public void stop() { stopRequested = true; }
    public void resetStopFlag() { stopRequested = false; }
    public boolean isStopRequested() { return stopRequested; }

    public void crawlSite(SiteEntity site) {
        ForkJoinPool pool = new ForkJoinPool();
        Set<String> visitedLinks = Collections.synchronizedSet(new HashSet<>());

        try {
            SiteCrawlerTask rootTask = new SiteCrawlerTask(
                    site.getUrl(), site, pageRepository, lemmaRepository,
                    indexRepository, lemmaService, visitedLinks, this
            );
            pool.invoke(rootTask);
        } finally {
            site.setStatus(stopRequested ? Status.FAILED : Status.INDEXED);
            if (stopRequested) site.setLastError("Индексация остановлена пользователем");
            siteRepository.save(site);
            pool.shutdown();
        }
    }

    // Вынесла логику сохранения в отдельный метод, чтобы оба класса могли его юзать
    public void processAndSavePage(SiteEntity site, String url, int statusCode, Document doc) throws Exception {
        URI uri = new URI(url);
        String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
        if (uri.getQuery() != null) path += "?" + uri.getQuery();

        if (!pageRepository.existsBySiteAndPath(site, path)) {
            PageEntity page = new PageEntity();
            page.setSite(site);
            page.setPath(path);
            page.setCode(statusCode);
            page.setContent(doc != null ? doc.outerHtml() : "");
            pageRepository.save(page);

            if (doc != null) {
                String text = lemmaService.cleanHtml(doc.outerHtml());
                var lemmas = lemmaService.getLemmas(text);

                for (var entry : lemmas.entrySet()) {
                    saveLemmaAndIndex(page, site, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void saveLemmaAndIndex(PageEntity page, SiteEntity site, String lemmaWord, int count) {
        LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSite(lemmaWord, site)
                .orElseGet(() -> {
                    LemmaEntity newLemma = new LemmaEntity();
                    newLemma.setLemma(lemmaWord);
                    newLemma.setFrequency(0);
                    newLemma.setSite(site);
                    return newLemma;
                });
        lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
        lemmaRepository.save(lemmaEntity);

        IndexEntity index = new IndexEntity();
        index.setPage(page);
        index.setLemma(lemmaEntity);
        index.setRank(count);
        indexRepository.save(index);
    }

    // Твой метод для одной страницы оставляем почти без изменений
    public void indexSinglePageOnly(SiteEntity site, String url) throws Exception {
        // ... (твой существующий код indexSinglePageOnly)
    }
}