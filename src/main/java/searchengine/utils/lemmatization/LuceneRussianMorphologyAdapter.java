package searchengine.utils.lemmatization;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class LuceneRussianMorphologyAdapter implements LemmaFinder {

    private final Analyzer analyzer;

    public LuceneRussianMorphologyAdapter() {
        // Создаем RussianAnalyzer с кастомным набором стоп-слов
        Set<String> stopWords = Set.of(
                "и", "в", "на", "с", "по", "за", "к", "до", "из", "у", "от", "о", "об",
                "со", "во", "не", "ни", "да", "но", "или", "ли", "бы", "же", "ведь", "мол"
        );
        this.analyzer = new RussianAnalyzer();
    }

    @Override
    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        if (text == null || text.isBlank()) {
            return lemmas;
        }

        try (TokenStream tokenStream = analyzer.tokenStream("", new StringReader(text))) {
            tokenStream.reset();
            CharTermAttribute attribute = tokenStream.addAttribute(CharTermAttribute.class);

            while (tokenStream.incrementToken()) {
                String lemma = attribute.toString();
                if (lemma.length() > 2) { // Игнорируем короткие слова
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            }

            tokenStream.end();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при лемматизации текста", e);
        }

        return lemmas;
    }

    @Override
    public void close() {
        analyzer.close();
    }
}