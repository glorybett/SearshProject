package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Config;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repository.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Config config;
    private final LemmatizationService lemmatizationService;
    private final PageIndexer pageIndexer;

    private ForkJoinPool pool;
    private volatile boolean indexingStopped = false;
    private final Map<String, Set<String>> processedUrls = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public boolean startIndexing() {
        try {
            if (isIndexingRunning()) {
                log.warn("Indexing already in progress");
                return false;
            }

            indexingStopped = false;
            pool = new ForkJoinPool();
            processedUrls.clear();

            sites.getSites().forEach(configSite -> {
                if (indexingStopped) return;

                Optional<Site> existingSite = siteRepository.findFirstByUrl(configSite.getUrl());
                Site site = existingSite.orElseGet(Site::new);

                if (existingSite.isPresent()) {
                    clearSiteData(site);
                }

                site.setUrl(configSite.getUrl());
                site.setName(configSite.getName());
                site.setStatus(Site.Status.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(null);
                siteRepository.save(site);

                processedUrls.put(site.getUrl(), Collections.synchronizedSet(new HashSet<>()));
                pool.execute(new SiteIndexer(
                        site,
                        siteRepository,
                        pageRepository,
                        lemmaRepository,
                        indexRepository,
                        lemmatizationService,
                        config.getUserAgent(),
                        config.getReferrer(),
                        config.getDelay(),
                        processedUrls.get(site.getUrl())
                ));
            });

            new Thread(this::monitorIndexing).start();
            return true;
        } catch (Exception e) {
            log.error("Error starting indexing", e);
            stopIndexing();
            return false;
        }
    }

    private void monitorIndexing() {
        pool.shutdown();
        try {
            while (!pool.isTerminated()) {
                Thread.sleep(1000);
            }
            log.info("Indexing completed successfully");
        } catch (InterruptedException e) {
            log.error("Indexing interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            pool = null;
            processedUrls.clear();
        }
    }

    @Transactional
    protected void clearSiteData(Site site) {
        if (site.getId() != null) {
            indexRepository.deleteBySite(site);
            lemmaRepository.deleteBySite(site);
            pageRepository.deleteBySite(site);
        }
    }

    @Override
    public boolean stopIndexing() {
        if (pool != null) {
            indexingStopped = true;
            pool.shutdownNow();

            List<Site> indexingSites = siteRepository.findByStatus(Site.Status.INDEXING);
            indexingSites.forEach(site -> {
                site.setStatus(Site.Status.FAILED);
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            });

            processedUrls.clear();
            return true;
        }
        return false;
    }

    @Override
    public boolean isIndexingRunning() {
        return pool != null && !pool.isShutdown() && !pool.isTerminated();
    }

    @Override
    @Transactional
    public ResponseEntity<Map<String, Object>> indexPage(String url) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<searchengine.config.Site> configSite = sites.getSites().stream()
                    .filter(s -> url.startsWith(s.getUrl()))
                    .findFirst();

            if (configSite.isEmpty()) {
                response.put("result", false);
                response.put("error", "Page outside of config sites");
                return ResponseEntity.badRequest().body(response);
            }

            Site site = siteRepository.findFirstByUrl(configSite.get().getUrl())
                    .orElseGet(() -> {
                        Site newSite = new Site();
                        newSite.setUrl(configSite.get().getUrl());
                        newSite.setName(configSite.get().getName());
                        newSite.setStatus(Site.Status.INDEXING);
                        return siteRepository.save(newSite);
                    });

            pageIndexer.index(site, url);
            response.put("result", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Indexing error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}