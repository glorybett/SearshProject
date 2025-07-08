package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageIndexer {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmatizationService lemmatizationService;

    public void index(Site site, String url) {
        String baseUrl = site.getUrl().endsWith("/")
                ? site.getUrl().substring(0, site.getUrl().length() - 1)
                : site.getUrl();

        String path = url.replaceFirst(baseUrl, "");
        path = path.isEmpty() ? "/" : path;

        // Удаление существующей страницы
        Optional<Page> existingPage = pageRepository.findByPathAndSite(path, site);
        existingPage.ifPresent(page -> {
            indexRepository.deleteByPage(page);
            lemmaRepository.decrementFrequencyForPage(page);
            pageRepository.delete(page);
        });

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("SearchEngineBot")
                    .referrer("https://www.google.com")
                    .timeout(10_000)
                    .execute();

            if (response.statusCode() >= 400) {
                log.warn("Skipping page with error code: {} - {}", response.statusCode(), url);
                return;
            }

            Document doc = response.parse();
            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(doc.html());
            pageRepository.save(page);

            processContent(page);
        } catch (IOException e) {
            log.error("Error indexing page: {}", url, e);
        }
    }

    @Transactional
    private void processContent(Page page) {
        String cleanText = lemmatizationService.cleanHtml(page.getContent());
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanText);

        lemmas.forEach((lemmaText, count) -> {
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setLemma(lemmaText);
                        newLemma.setSite(page.getSite());
                        newLemma.setFrequency(0);
                        return lemmaRepository.save(newLemma);
                    });

            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            indexRepository.save(index);
        });
    }
}