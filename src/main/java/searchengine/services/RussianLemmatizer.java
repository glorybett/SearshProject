package searchengine.services;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

@Service
public class RussianLemmatizer {
    private final MorfologikAnalyzer analyzer;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "и", "в", "на", "с", "по", "за", "к", "до", "из", "у", "от", "о", "об",
            "со", "во", "не", "ни", "да", "но", "или", "ли", "бы", "же", "ведь", "мол",
            "под", "при", "то", "это", "как", "так", "что", "вот", "вроде", "типа"
    ));

    public RussianLemmatizer() {
        this.analyzer = new MorfologikAnalyzer();
    }

    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        if (text == null || text.isBlank()) {
            return lemmas;
        }

        text = text.toLowerCase();

        try (TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(text))) {
            tokenStream.reset();
            CharTermAttribute attribute = tokenStream.addAttribute(CharTermAttribute.class);

            while (tokenStream.incrementToken()) {
                String lemma = attribute.toString();
                if (lemma.length() > 2 && !STOP_WORDS.contains(lemma)) {
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            }
            tokenStream.end();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка лемматизации", e);
        }

        return lemmas;
    }

    public List<String> getLemmaList(String text) {
        return new ArrayList<>(getLemmas(text).keySet());
    }

    @PreDestroy
    public void close() {
        analyzer.close();
    }
}