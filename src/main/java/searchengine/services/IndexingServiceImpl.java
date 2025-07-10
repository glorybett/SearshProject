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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Map<String, AtomicBoolean> indexingFlags = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public boolean startIndexing() {
        try {
            if (isIndexingRunning()) {
                log.warn("Indexing already in progress");
                return false;
            }

            indexingStopped = false;
            pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            processedUrls.clear();
            indexingFlags.clear();

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

                Set<String> siteProcessedUrls = ConcurrentHashMap.newKeySet();
                processedUrls.put(site.getUrl(), siteProcessedUrls);

                AtomicBoolean siteIndexingFlag = new AtomicBoolean(false);
                indexingFlags.put(site.getUrl(), siteIndexingFlag);

                pool.execute(new SiteIndexer(
                        site,
                        site.getUrl(),
                        siteRepository,
                        pageRepository,
                        lemmaRepository,
                        indexRepository,
                        lemmatizationService,
                        config.getUserAgent(),
                        config.getReferrer(),
                        config.getDelay(),
                        siteProcessedUrls,
                        siteIndexingFlag
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
        try {
            while (!pool.isTerminated()) {
                updateSiteStatuses();
                Thread.sleep(5000);
            }
            updateSiteStatuses();
            log.info("Indexing completed successfully");
        } catch (InterruptedException e) {
            log.error("Indexing monitoring interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            pool = null;
            processedUrls.clear();
            indexingFlags.clear();
        }
    }

    private void updateSiteStatuses() {
        sites.getSites().forEach(configSite -> {
            Site site = siteRepository.findFirstByUrl(configSite.getUrl()).orElse(null);
            if (site != null && site.getStatus() == Site.Status.INDEXING) {
                long processedCount = processedUrls.getOrDefault(site.getUrl(), Collections.emptySet()).size();

                if (processedCount > 0) {
                    site.setStatusTime(LocalDateTime.now());
                    if (pool.isQuiescent()) {
                        site.setStatus(Site.Status.INDEXED);
                    }
                    siteRepository.save(site);
                }
            }
        });
    }

    @Override
    public boolean stopIndexing() {
        if (pool != null) {
            indexingStopped = true;
            indexingFlags.values().forEach(flag -> flag.set(true));
            pool.shutdownNow();

            List<Site> indexingSites = siteRepository.findByStatus(Site.Status.INDEXING);
            indexingSites.forEach(site -> {
                site.setStatus(Site.Status.FAILED);
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            });

            processedUrls.clear();
            indexingFlags.clear();
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
                        newSite.setStatusTime(LocalDateTime.now());
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

    @Transactional
    protected void clearSiteData(Site site) {
        if (site.getId() != null) {
            indexRepository.deleteBySite(site);
            lemmaRepository.deleteBySite(site);
            pageRepository.deleteBySite(site);
        }
    }
}