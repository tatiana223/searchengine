package searchengine.services.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    @Transactional
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


    public void processAndSavePage(SiteEntity site, String url, int statusCode, Document doc) throws Exception {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
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

    public void indexSinglePageOnly(SiteEntity site, String url) {
        System.out.println("=== НАЧАЛО indexSinglePageOnly ===");
        System.out.println("URL: " + url);
        System.out.println("Сайт: " + site.getName() + " (" + site.getUrl() + ")");

        try {
            URI uri = new URI(url);
            String normalizedUrl = uri.normalize().toString();
            System.out.println("Нормализованный URL: " + normalizedUrl);

            System.out.println("Подключаемся к странице...");
            Connection.Response response = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreHttpErrors(true)
                    .timeout(30000)
                    .execute();

            int statusCode = response.statusCode();
            System.out.println("Код ответа HTTP: " + statusCode);

            if (statusCode != 200) {
                System.out.println("Пропускаем страницу с ошибкой HTTP: " + statusCode);
                return;
            }

            System.out.println("Парсим HTML...");
            Document doc = Jsoup.parse(response.body(), normalizedUrl);
            System.out.println("HTML получен, размер: " + doc.outerHtml().length() + " символов");

            String title = doc.title();
            System.out.println("Заголовок страницы: " + title);

            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }
            System.out.println("Путь страницы: " + path);

            PageEntity page = pageRepository.findBySiteAndPath(site, path)
                    .orElse(new PageEntity());

            page.setSite(site);
            page.setPath(path);
            page.setCode(statusCode);
            page.setContent(doc.outerHtml());
            pageRepository.save(page);
            System.out.println("Страница обновлена/создана ID: " + page.getId());

            System.out.println("Удаляем старые индексы...");
            List<IndexEntity> oldIndexes = indexRepository.findAllByPage(page);
            System.out.println("Найдено старых индексов: " + oldIndexes.size());

            for (IndexEntity oldIndex : oldIndexes) {
                LemmaEntity lemma = oldIndex.getLemma();
                lemma.setFrequency(lemma.getFrequency() - 1);
                if (lemma.getFrequency() <= 0) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemmaRepository.save(lemma);
                }
                indexRepository.delete(oldIndex);
            }

            System.out.println("Начинаем индексацию контента...");
            indexPageContent(page, site, doc);

            System.out.println("=== УСПЕШНО ЗАВЕРШЕНО ===");

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void indexPageContent(PageEntity page, SiteEntity site, Document doc) {
        System.out.println("=== НАЧАЛО indexPageContent ===");

        try {
            System.out.println("Очищаем HTML");
            String text = lemmaService.cleanHtml(doc.outerHtml());
            System.out.println("Размер очищенного текста: " + text.length() + " символов");

            if (text.length() < 10) {
                System.out.println("Текст слишком короткий, возможно проблема с очисткой HTML");
                System.out.println("Первые 500 символов HTML: " + doc.outerHtml().substring(0, Math.min(500, doc.outerHtml().length())));
            }

            System.out.println("Получаем леммы...");
            var lemmas = lemmaService.getLemmas(text);
            System.out.println("Найдено лемм: " + lemmas.size());

            if (lemmas.isEmpty()) {
                System.out.println("Леммы не найдены!");
                return;
            }

            System.out.println("Первые 5 лемм:");
            lemmas.entrySet().stream()
                    .limit(3)
                    .forEach(entry -> System.out.println("   " + entry.getKey() + " = " + entry.getValue()));

            System.out.println("Сохраняем леммы в БД...");
            int savedCount = 0;
            for (var entry : lemmas.entrySet()) {
                String lemmaWord = entry.getKey();
                int count = entry.getValue();

                LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSite(lemmaWord, site)
                        .orElseGet(() -> {
                            LemmaEntity newLemma = new LemmaEntity();
                            newLemma.setLemma(lemmaWord);
                            newLemma.setFrequency(0);
                            newLemma.setSite(site);
                            return lemmaRepository.save(newLemma);
                        });

                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                lemmaRepository.save(lemmaEntity);

                IndexEntity index = indexRepository.findByPageAndLemma(page, lemmaEntity)
                        .orElse(new IndexEntity());
                index.setPage(page);
                index.setLemma(lemmaEntity);
                index.setRank(count);
                indexRepository.save(index);

                savedCount++;
            }

            System.out.println("Сохранено лемм: " + savedCount);
            System.out.println("=== КОНЕЦ indexPageContent ===");

        } catch (Exception e) {
            System.err.println("Ошибка в indexPageContent: " + e.getMessage());
            e.printStackTrace();
        }
    }



}
