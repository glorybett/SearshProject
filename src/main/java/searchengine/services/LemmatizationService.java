package searchengine.services;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LemmatizationService {
    private final RussianLemmatizer lemmatizer;

    @Autowired
    public LemmatizationService(RussianLemmatizer lemmatizer) {
        this.lemmatizer = lemmatizer;
    }

    public Map<String, Integer> getLemmas(String text) {
        return lemmatizer.getLemmas(cleanHtml(text));
    }

    public List<String> getLemmaList(String text) {
        return lemmatizer.getLemmaList(cleanHtml(text));
    }

    public String cleanHtml(String html) {
        if (html == null) return "";
        return Jsoup.parse(html).text();
    }

    public void close() {
    }
}