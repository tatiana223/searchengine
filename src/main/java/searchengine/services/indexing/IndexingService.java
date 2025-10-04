package searchengine.services.indexing;

import searchengine.exception.IndexingAlreadyStartedException;

public interface IndexingService {
    void startIndexing() throws IndexingAlreadyStartedException;
    void stopIndexing();
    boolean indexPage(String url);
}
