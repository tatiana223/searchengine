package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<SiteEntity> sites = siteRepository.findAll();

        total.setSites(sites.size());

        total.setIndexing(sites.stream().anyMatch(s -> s.getStatus() == Status.INDEXING));

        int totalPages = 0;
        int totalLemmas = 0;

        for (SiteEntity site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC) * 1000);
            item.setError(site.getLastError());

            int pages = pageRepository.countBySite(site);
            item.setPages(pages);

            int lemmas = lemmaRepository.countBySite(site);
            item.setLemmas(lemmas);

            totalPages += pages;
            totalLemmas += lemmas;

            detailed.add(item);

        }
        total.setPages(totalPages);
        total.setLemmas(totalLemmas);

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);

        return response;
    }

}
