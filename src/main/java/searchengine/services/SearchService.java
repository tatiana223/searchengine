package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.LemmaService;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private LemmaService lemmaService;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    private static final double FREQUENCY_THRESHOLD = 0.8;

    public Map<String, Object> search(String query, String siteUrl, int offset, int limit) {
        if (query == null || query.isBlank()) {
            return Map.of("result", false, "error", "Задан пустой поисковый запрос");
        }

        try {
            Map<String, Integer> queryLemmasMap = lemmaService.getLemmas(query);
            List<String> queryLemmasList = new ArrayList<>(queryLemmasMap.keySet());

            if (queryLemmasList.isEmpty()) {
                return Map.of("result", true, "count", 0, "data", List.of());
            }

            List<SiteEntity> sites = getSitesForSearch(siteUrl);
            if (sites.isEmpty()) {
                return Map.of("result", true, "count", 0, "data", List.of());
            }

            List<LemmaEntity> foundLemmas = lemmaRepository.findByLemmaInAndSiteIn(queryLemmasList, sites);

            if (foundLemmas.isEmpty()) {
                return Map.of("result", true, "count", 0, "data", List.of());
            }

            foundLemmas = filterAndSortLemmas(foundLemmas, sites);

            if (foundLemmas.isEmpty()) {
                return Map.of("result", true, "count", 0, "data", List.of());
            }

            List<PageEntity> pages = findPagesWithAllLemmas(foundLemmas);

            if (pages.isEmpty()) {
                return Map.of("result", true, "count", 0, "data", List.of());
            }

            List<SearchResult> searchResults = calculateRelevance(pages, foundLemmas);

            List<SearchResult> paginatedResults = applyPagination(searchResults, offset, limit);

            List<Map<String, Object>> resultData = paginatedResults.stream()
                    .map(this::convertToResultMap)
                    .collect(Collectors.toList());

            return Map.of(
                    "result", true,
                    "count", searchResults.size(),
                    "data", resultData
            );

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("result", false, "error", "Ошибка при выполнении поиска: " + e.getMessage());
        }
    }

    private List<SiteEntity> getSitesForSearch(String siteUrl) {
        if (siteUrl != null && !siteUrl.isEmpty()) {
            return siteRepository.findByUrl(siteUrl).map(List::of).orElse(List.of());
        } else {
            return siteRepository.findAll();
        }
    }

    private List<LemmaEntity> filterAndSortLemmas(List<LemmaEntity> lemmas, List<SiteEntity> sites) {
        Map<SiteEntity, Long> siteTotalPages = new HashMap<>();
        for (SiteEntity site : sites) {
            long count = pageRepository.countBySite(site);
            siteTotalPages.put(site, count);
        }

        List<LemmaEntity> filteredLemmas = lemmas.stream()
                .filter(lemma -> {
                    Long totalPages = siteTotalPages.get(lemma.getSite());
                    if (totalPages == null || totalPages == 0) return false;

                    double frequencyRatio = (double) lemma.getFrequency() / totalPages;
                    return frequencyRatio <= FREQUENCY_THRESHOLD;
                })
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .collect(Collectors.toList());

        return filteredLemmas;
    }

    private List<PageEntity> findPagesWithAllLemmas(List<LemmaEntity> lemmas) {
        List<PageEntity> pages = new ArrayList<>();
        int startIndex = 0;

        while (pages.isEmpty() && startIndex < lemmas.size()) {
            LemmaEntity firstUsefulLemma = lemmas.get(startIndex);
            List<IndexEntity> indexes = indexRepository.findByLemma(firstUsefulLemma);
            pages = indexes.stream()
                    .map(IndexEntity::getPage)
                    .collect(Collectors.toList());
            startIndex++;
        }

        if (pages.isEmpty()) {
            return List.of();
        }

        for (int i = startIndex; i < lemmas.size() && !pages.isEmpty(); i++) {
            LemmaEntity currentLemma = lemmas.get(i);
            List<PageEntity> pagesWithCurrentLemma = indexRepository.findPagesByLemma(currentLemma);
            pages.retainAll(pagesWithCurrentLemma);
        }

        return pages;
    }

    private List<SearchResult> calculateRelevance(List<PageEntity> pages, List<LemmaEntity> lemmas) {
        List<IndexEntity> allIndexes = indexRepository.findByPageInAndLemmaIn(pages, lemmas);

        Map<PageEntity, Double> pageToTotalRank = new HashMap<>();
        for (IndexEntity index : allIndexes) {
            PageEntity page = index.getPage();
            double rank = index.getRank();
            pageToTotalRank.merge(page, rank, Double::sum);
        }
        double maxRelevance = pageToTotalRank.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<PageEntity, Double> entry : pageToTotalRank.entrySet()) {
            double relativeRelevance = entry.getValue() / maxRelevance;
            results.add(new SearchResult(entry.getKey(), relativeRelevance, lemmas));
        }
        results.sort(Comparator.comparingDouble(SearchResult::getRelevance).reversed());
        return results;
    }

    private List<SearchResult> applyPagination(List<SearchResult> results, int offset, int limit) {
        if (offset >= results.size()) {
            return List.of();
        }

        int endIndex = Math.min(offset + limit, results.size());
        return results.subList(offset, endIndex);
    }

    private Map<String, Object> convertToResultMap(SearchResult searchResult) {
        PageEntity page = searchResult.getPage();
        String content = page.getContent();

        return Map.of(
                "site", page.getSite().getUrl(),
                "siteName", page.getSite().getName(),
                "uri", page.getPath(),
                "title", extractTitle(content),
                "snippet", generateSnippet(content, searchResult.getLemmas()),
                "relevance", Math.round(searchResult.getRelevance() * 100.0) / 100.0
        );
    }

    private String extractTitle(String htmlContent) {
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);
            String title = doc.title();
            return title != null && !title.isEmpty() ? title : "Без заголовка";
        } catch (Exception e) {
            return "Без заголовка";
        }
    }

    private String generateSnippet(String htmlContent, List<LemmaEntity> lemmas) {
        String cleanText = lemmaService.cleanHtml(htmlContent);

        if (cleanText.length() <= 250) {
            return highlightLemmas(cleanText, lemmas);
        }

        String snippet = cleanText.substring(0, 250) + "...";
        return highlightLemmas(snippet, lemmas);
    }

    private String highlightLemmas(String text, List<LemmaEntity> lemmas) {
        String result = text;
        List<String> lemmaWords = lemmas.stream()
                .map(LemmaEntity::getLemma)
                .collect(Collectors.toList());

        for (String lemma : lemmaWords) {
            result = result.replaceAll("(?i)" + Pattern.quote(lemma), "<b>" + lemma + "</b>");
        }

        return result;
    }

    private static class SearchResult {
        private final PageEntity page;
        private final double relevance;
        private final List<LemmaEntity> lemmas;

        public SearchResult(PageEntity page, double relevance, List<LemmaEntity> lemmas) {
            this.page = page;
            this.relevance = relevance;
            this.lemmas = lemmas;
        }

        public PageEntity getPage() { return page; }
        public double getRelevance() { return relevance; }
        public List<LemmaEntity> getLemmas() { return lemmas; }
    }
}