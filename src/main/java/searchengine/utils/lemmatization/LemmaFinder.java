package searchengine.utils.lemmatization;

import java.util.Map;

public interface LemmaFinder {
    Map<String, Integer> collectLemmas(String text);

    void close();
}
