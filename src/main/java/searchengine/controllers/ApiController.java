package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;
import searchengine.services.indexing.IndexingService;

import searchengine.exception.IndexingAlreadyStartedException;

import java.util.Map;
import java.util.Objects;


@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;


    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Autowired
    private IndexingService indexingService;
    @Autowired
    private SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        try {
            indexingService.startIndexing();
            return ResponseEntity.ok("{\"result\": true}");
        } catch (IndexingAlreadyStartedException e) {
            return ResponseEntity.badRequest().body("{\"result\": false, \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        indexingService.stopIndexing();
        return ResponseEntity.ok("{\"result\": true}");
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        try {
            boolean indexed = indexingService.indexPage(url);
            if (indexed) {
                return ResponseEntity.ok(Map.of("result", true));
            } else {
                return ResponseEntity.badRequest().body(
                        Map.of("result", false,
                                "error", "Данная страница находится за пределами сайтов, указанных в конфиг.файле")
                );
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("result", false, "error", e.getMessage())
            );
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        try {
            var result = searchService.search(query, site, offset, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", e.getMessage()));
        }
    }
}
