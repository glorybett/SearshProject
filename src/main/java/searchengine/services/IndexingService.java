package searchengine.services;

import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IndexingService {
    boolean startIndexing();

    boolean stopIndexing();

    boolean isIndexingRunning();

    ResponseEntity<Map<String, Object>> indexPage(String url);
}