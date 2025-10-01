package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;
import searchengine.services.indexing.IndexingService;

import searchengine.exception.IndexingAlreadyStartedException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Autowired
    private IndexingService indexingService;
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
        return ResponseEntity.ok("{\"result\": true");
    }
}
