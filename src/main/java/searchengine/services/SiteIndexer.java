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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class SiteIndexer extends RecursiveAction {
    private final Site site;
    private final String url;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmatizationService lemmatizationService;
    private final String userAgent;
    private final String referrer;
    private final int delay;
    private final Set<String> processedUrls;
    private final AtomicBoolean indexingStopped;

    @Override
    protected void compute() {
        if (indexingStopped.get() || processedUrls.contains(url)) {
            return;
        }

        try {
            processedUrls.add(url);
            indexPage();

            if (indexingStopped.get()) return;

            List<SiteIndexer> tasks = new ArrayList<>();
            Set<String> newUrls = getChildUrls();

            for (String childUrl : newUrls) {
                if (indexingStopped.get()) break;

                tasks.add(new SiteIndexer(
                        site,
                        childUrl,
                        siteRepository,
                        pageRepository,
                        lemmaRepository,
                        indexRepository,
                        lemmatizationService,
                        userAgent,
                        referrer,
                        delay,
                        processedUrls,
                        indexingStopped
                ));
            }

            ForkJoinTask.invokeAll(tasks);
        } catch (Exception e) {
            log.error("Error indexing URL: {} | {}", url, e.getMessage());
            site.setStatus(Site.Status.FAILED);
            site.setLastError("Indexing error: " + e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void indexPage() throws IOException, InterruptedException {
        if (indexingStopped.get()) return;

        Thread.sleep(delay);

        Connection.Response response = Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(30_000)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .execute();

        if (response.statusCode() >= 400) {
            log.warn("Skipping page with error code: {} - {}", response.statusCode(), url);
            return;
        }

        Document doc = response.parse();
        String baseUrl = site.getUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String path = url.substring(baseUrl.length());
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
    }

    private Set<String> getChildUrls() throws IOException {
        if (indexingStopped.get()) return Collections.emptySet();

        Connection.Response response = Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(10_000)
                .execute();

        Document doc = response.parse();
        Elements links = doc.select("a[href]");
        Set<String> newUrls = new HashSet<>();

        for (Element link : links) {
            if (indexingStopped.get()) break;

            String childUrl = link.absUrl("href")
                    .replaceAll("#.*$", "")
                    .replaceAll("(?<!:)/+", "/")
                    .trim();

            if (isValidUrl(childUrl) && !processedUrls.contains(childUrl)) {
                newUrls.add(childUrl);
            }
        }

        return newUrls;
    }

    @Transactional
    protected void processPageContent(Page page) {
        String cleanText = lemmatizationService.cleanHtml(page.getContent());
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanText);

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            if (indexingStopped.get()) break;

            String lemmaText = entry.getKey();
            int count = entry.getValue();

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
        }
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) &&
                !url.matches(".*\\.(pdf|jpg|png|gif|zip|docx?|xlsx?|pptx?|js|css|xml|json)$") &&
                !url.contains("#") &&
                !url.matches(".*/feed/?$") &&
                !url.matches(".*/amp/?$") &&
                !url.matches(".*\\?.*=");
    }
}