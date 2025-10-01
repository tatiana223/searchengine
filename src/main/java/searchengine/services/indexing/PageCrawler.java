package searchengine.services.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class PageCrawler {
    private volatile boolean stopRequested = false;
    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Set<String> visitedLinks = new HashSet<>();

    public void stop() {
        stopRequested = true;
        visitedLinks.clear();
    }

    public void crawlSite(SiteEntity site) {
        stopRequested = false;
        try {
            crawlPage(site, site.getUrl());
        } finally {
            if (stopRequested) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
            } else {
                site.setStatus(Status.INDEXED);
            }
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    private void crawlPage(SiteEntity site, String url) {
        if (stopRequested) {
            return;
        }
        if (url.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg|pdf|mp4|mp3)$")) {
            System.out.println("Пропущен не-HTML URL: " + url);
            return;
        }

        try {
            URI uri = new URI(url);
            String normalizedUrl = uri.normalize().toString();
            if (visitedLinks.contains(normalizedUrl)) {
                return;
            }
            visitedLinks.add(normalizedUrl);

            Connection.Response response = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreHttpErrors(true)
                    .execute();

            int statusCode = response.statusCode();;
            Document doc = null;

            if (statusCode == 200 && response.contentType() != null && response.contentType().contains("text/html")) {
                doc = Jsoup.parse(response.body(), normalizedUrl);
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }

            if (!pageRepository.existsBySiteAndPath(site, path)) {
                PageEntity page = new PageEntity();
                page.setSite(site);
                page.setPath(path);
                page.setCode(statusCode);
                page.setContent(doc != null ? doc.outerHtml() : "");
                pageRepository.save(page);
            }

            if (doc != null) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String absUrl = link.absUrl("href");
                    if (absUrl.startsWith(site.getUrl())) {
                        System.out.println("Нашёл ссылку: " + absUrl);
                        crawlPage(site, absUrl);
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

}
