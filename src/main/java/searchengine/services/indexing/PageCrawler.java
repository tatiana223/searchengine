package searchengine.services.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
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
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LemmaService lemmaService;
    private Set<String> visitedLinks = new HashSet<>();

    public void stop() {
        stopRequested = true;
        visitedLinks.clear();
    }

    public void crawlSite(SiteEntity site) {
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

    public void crawlSinglePage(SiteEntity site, String url) {
        crawlPage(site, url);
    }

    private void crawlPage(SiteEntity site, String url) {
        if (stopRequested) {
            System.out.println("Остановка индексации, выходим: " + url);
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
                System.out.println("Уже посещали: " + url);
                return;
            }
            visitedLinks.add(normalizedUrl);

            System.out.println("Загружаем: " + url);

            if (stopRequested) return;

            Connection.Response response = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreHttpErrors(true)
                    .execute();

            int statusCode = response.statusCode();;
            Document doc = null;

            if (statusCode == 200 && response.contentType() != null && response.contentType().contains("text/html")) {
                doc = Jsoup.parse(response.body(), normalizedUrl);
                System.out.println("Успешно загружено (" + statusCode + ")");
            } else {
                System.out.println("Ошибка загрузки (" + statusCode + ")");
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
                System.out.println("Сохранена страница id=" + page.getId() + " path=" + path);

                if (doc != null) {
                    String text = lemmaService.cleanHtml(doc.outerHtml());
                    var lemmas = lemmaService.getLemmas(text);
                    System.out.println("Найдено лемм: " + lemmas.size());

                    for (var entry : lemmas.entrySet()) {
                        String lemmaWord = entry.getKey();
                        int count = entry.getValue();
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

                        if (indexRepository.findByPageAndLemma(page, lemmaEntity).isEmpty()) {
                            IndexEntity index = new IndexEntity();
                            index.setPage(page);
                            index.setLemma(lemmaEntity);
                            index.setRank(count);
                            indexRepository.save(index);
                        }
                    }
                }
            }

            if (doc != null) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    if (stopRequested) {
                        System.out.println("Остановка индексации, выходим из рекурсии");
                        break; // сразу выходим из цикла
                    }
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
