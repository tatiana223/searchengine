package searchengine.services.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.*;
import searchengine.repository.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

public class SiteCrawlerTask extends RecursiveAction {
    private final String url;
    private final SiteEntity site;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final Set<String> visitedLinks;
    private final PageCrawler crawler;

    public SiteCrawlerTask(String url, SiteEntity site, PageRepository pageRepository,
                           LemmaRepository lemmaRepository, IndexRepository indexRepository,
                           LemmaService lemmaService, Set<String> visitedLinks, PageCrawler crawler) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.lemmaService = lemmaService;
        this.visitedLinks = visitedLinks;
        this.crawler = crawler;
    }

    @Override
    protected void compute() {
        if (crawler.isStopRequested()) return;

        if (url.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg|pdf|mp4|mp3)$")) return;

        try {
            URI uri = new URI(url);
            String normalizedUrl = uri.normalize().toString();

            if (!visitedLinks.add(normalizedUrl)) return;

            Thread.sleep(150);

            Connection.Response response = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("http://www.google.com")
                    .ignoreHttpErrors(true)
                    .execute();

            Document doc = null;
            if (response.statusCode() == 200 && response.contentType() != null && response.contentType().contains("text/html")) {
                doc = Jsoup.parse(response.body(), normalizedUrl);
            }

            crawler.processAndSavePage(site, url, response.statusCode(), doc);

            if (doc != null) {
                Elements links = doc.select("a[href]");
                List<SiteCrawlerTask> subTasks = new ArrayList<>();

                for (Element link : links) {
                    if (crawler.isStopRequested()) break;

                    String absUrl = link.absUrl("href");
                    if (absUrl.startsWith(site.getUrl()) && !visitedLinks.contains(absUrl)) {
                        SiteCrawlerTask task = new SiteCrawlerTask(absUrl, site, pageRepository,
                                lemmaRepository, indexRepository, lemmaService, visitedLinks, crawler);
                        task.fork();
                        subTasks.add(task);
                    }
                }

                for (SiteCrawlerTask task : subTasks) {
                    task.join();
                }
            }

        } catch (Exception e) {
            System.err.println("Ошибка при обработке " + url + ": " + e.getMessage());
        }
    }
}