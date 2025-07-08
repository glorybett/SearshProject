package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@Slf4j
@RequiredArgsConstructor
public class SiteIndexer extends RecursiveAction {
    private final Site site;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmatizationService lemmatizationService;
    private final String userAgent;
    private final String referrer;
    private final int delay;
    private final Set<String> processedUrls;

    @Override
    protected void compute() {
        try {
            if (Thread.interrupted() || processedUrls.contains(site.getUrl())) {
                return;
            }

            processedUrls.add(site.getUrl());
            indexSite(site.getUrl());
            site.setStatus(Site.Status.INDEXED);
        } catch (Exception e) {
            log.error("Indexing failed for site: {}", site.getUrl(), e);
            site.setStatus(Site.Status.FAILED);
            site.setLastError(e.getMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void indexSite(String url) {
        try {
            Thread.sleep(delay);

            Connection connection = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .followRedirects(true);

            Connection.Response response = connection.execute();
            if (response.statusCode() >= 400) {
                log.warn("Skipping page with error code: {} - {}", response.statusCode(), url);
                return;
            }

            Document doc = response.parse();
            String path = url.substring(site.getUrl().length());
            path = path.isEmpty() ? "/" : path;

            Optional<Page> existingPage = pageRepository.findByPathAndSite(path, site);
            existingPage.ifPresent(page -> {
                indexRepository.deleteByPage(page);
                lemmaRepository.decrementFrequencyForPage(page);
                pageRepository.delete(page);
            });

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(doc.html());
            pageRepository.save(page);

            processPageContent(page);

            Elements links = doc.select("a[href]");
            List<SiteIndexer> tasks = new ArrayList<>();

            for (Element link : links) {
                String childUrl = link.absUrl("href").replaceAll("#.*$", "");
                if (isValidUrl(childUrl) && !processedUrls.contains(childUrl)) {
                    processedUrls.add(childUrl);
                    tasks.add(new SiteIndexer(
                            site,
                            siteRepository,
                            pageRepository,
                            lemmaRepository,
                            indexRepository,
                            lemmatizationService,
                            userAgent,
                            referrer,
                            delay,
                            processedUrls
                    ));
                }
            }

            invokeAll(tasks);
        } catch (IOException | InterruptedException e) {
            log.error("Error indexing URL: {} | {}", url, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    protected void processPageContent(Page page) {
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

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) &&
                !url.matches(".*\\.(pdf|jpg|png|gif|zip|docx?|xlsx?|pptx?)$") &&
                !url.contains("?") &&
                !url.contains("#");
    }
}