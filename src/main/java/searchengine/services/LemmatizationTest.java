package searchengine.services;

import java.util.Map;

public class LemmatizationTest {
    public static void main(String[] args) {
        // Создаем зависимость RussianLemmatizer
        RussianLemmatizer russianLemmatizer = new RussianLemmatizer();

        // Передаем зависимость в конструктор
        LemmatizationService lemmatizer = new LemmatizationService(russianLemmatizer);

        String text = "Повторное появление леопарда в Осетии позволяет предположить, " +
                "что леопард постоянно обитает в некоторых районах Северного Кавказа.";

        Map<String, Integer> lemmas = lemmatizer.getLemmas(text);

        System.out.println("Результат лемматизации:");
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            System.out.println(entry.getKey() + "\t—\t" + entry.getValue());
        }

        lemmatizer.close();
    }
}