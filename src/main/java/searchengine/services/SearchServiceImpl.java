package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchData;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.*;
import searchengine.repository.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmatizationService lemmatizationService;
    private final SiteRepository siteRepository;

    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        log.info("Starting search for query: '{}' on site: {}", query, siteUrl);
        SearchResponse response = new SearchResponse();

        try {
            if (query == null || query.trim().isEmpty()) {
                log.warn("Empty search query");
                response.setResult(false);
                response.setError("Задан пустой поисковый запрос");
                return response;
            }

            List<String> queryLemmas = lemmatizationService.getLemmaList(query);
            log.debug("Extracted lemmas from query: {}", queryLemmas);

            if (queryLemmas.isEmpty()) {
                log.warn("No lemmas found in query: {}", query);
                response.setResult(false);
                response.setError("Не удалось извлечь леммы из поискового запроса");
                return response;
            }

            Site site = siteUrl != null ?
                    siteRepository.findByUrl(siteUrl).orElse(null) : null;
            log.debug("Site for search: {}", site != null ? site.getUrl() : "all sites");

            List<Lemma> filteredLemmas = filterAndSortLemmas(queryLemmas, site);
            log.debug("Filtered lemmas: {}",
                    filteredLemmas.stream().map(Lemma::getLemma).collect(Collectors.toList()));

            if (filteredLemmas.isEmpty()) {
                log.info("No relevant lemmas found after filtering");
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }

            List<Page> foundPages = findPagesContainingAllLemmas(filteredLemmas);
            log.debug("Found {} pages containing all lemmas", foundPages.size());

            List<SearchData> searchData = buildSearchResults(foundPages, queryLemmas, offset, limit);
            log.info("Search completed successfully, found {} results", searchData.size());

            response.setResult(true);
            response.setCount(foundPages.size());
            response.setData(searchData);
        } catch (Exception e) {
            log.error("Search error", e);
            response.setResult(false);
            response.setError("Ошибка поиска: " + e.getMessage());
        }
        return response;
    }

    private List<Lemma> filterAndSortLemmas(List<String> lemmas, Site site) {
        List<Lemma> result = new ArrayList<>();

        for (String lemma : lemmas) {
            List<Lemma> foundLemmas = site != null ?
                    lemmaRepository.findByLemmaInAndSite(List.of(lemma), site) :
                    lemmaRepository.findByLemma(lemma);

            foundLemmas.forEach(l -> {
                long totalPages = pageRepository.countBySite(l.getSite());
                if (totalPages > 0) {
                    double frequencyRatio = (double) l.getFrequency() / totalPages;
                    if (frequencyRatio < 0.8) {
                        result.add(l);
                    }
                }
            });
        }

        return result.stream()
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());
    }

    private List<Page> findPagesContainingAllLemmas(List<Lemma> lemmas) {
        if (lemmas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Page> pages = indexRepository.findPagesByLemma(lemmas.get(0));
        log.debug("Initial pages for lemma {}: {}", lemmas.get(0).getLemma(), pages.size());

        for (int i = 1; i < lemmas.size() && !pages.isEmpty(); i++) {
            pages = indexRepository.findPagesByLemmaAndPageIn(lemmas.get(i), pages);
            log.debug("Pages after filtering by lemma {}: {}",
                    lemmas.get(i).getLemma(), pages.size());
        }

        return pages;
    }

    private List<SearchData> buildSearchResults(List<Page> pages,
                                                List<String> queryLemmas,
                                                int offset,
                                                int limit) {
        if (pages.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate relevance
        Map<Page, Double> relevanceMap = new HashMap<>();
        double maxRelevance = 0;

        for (Page page : pages) {
            Double relevance = indexRepository.sumRankByPageAndLemmas(page, queryLemmas);
            if (relevance != null && relevance > 0) {
                relevanceMap.put(page, relevance);
                if (relevance > maxRelevance) {
                    maxRelevance = relevance;
                }
            }
        }

        // Sort and paginate results
        double finalMaxRelevance = maxRelevance;
        return pages.stream()
                .filter(relevanceMap::containsKey)
                .sorted((p1, p2) -> Double.compare(
                        relevanceMap.get(p2),
                        relevanceMap.get(p1)))
                .skip(offset)
                .limit(limit)
                .map(page -> createSearchData(page, queryLemmas, relevanceMap.get(page), finalMaxRelevance))
                .collect(Collectors.toList());
    }

    private SearchData createSearchData(Page page,
                                        List<String> queryLemmas,
                                        double relevance,
                                        double maxRelevance) {
        SearchData data = new SearchData();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(extractTitle(page.getContent()));
        data.setSnippet(generateSnippet(page.getContent(), queryLemmas));
        data.setRelevance((float) (relevance / maxRelevance));
        return data;
    }

    private String extractTitle(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Element title = doc.selectFirst("title");
            return title != null ? title.text() : "Без названия";
        } catch (Exception e) {
            log.warn("Error extracting title", e);
            return "Без названия";
        }
    }

    private String generateSnippet(String html, List<String> queryLemmas) {
        String cleanText = lemmatizationService.cleanHtml(html);

        // Split into sentences
        String[] sentences = cleanText.split("(?<=[.!?])\\s+");

        // Find the best sentence containing most query lemmas
        String bestSentence = null;
        int maxMatches = 0;

        for (String sentence : sentences) {
            String lowerSentence = sentence.toLowerCase();
            int matches = 0;

            for (String lemma : queryLemmas) {
                if (lowerSentence.contains(lemma.toLowerCase())) {
                    matches++;
                }
            }

            if (matches > maxMatches) {
                maxMatches = matches;
                bestSentence = sentence;
                if (matches == queryLemmas.size()) {
                    break; // Нашли идеальное совпадение
                }
            }
        }

        // Use the best sentence or first 250 chars if no good sentence found
        String snippet = (bestSentence != null) ? bestSentence :
                (cleanText.length() > 250 ? cleanText.substring(0, 250) : cleanText);

        // Выделить ключевые слова
        for (String lemma : queryLemmas) {
            snippet = snippet.replaceAll(
                    "(?i)(" + Pattern.quote(lemma) + ")",
                    "<b>$1</b>");
        }

        return snippet.length() > 250 ?
                snippet.substring(0, 250) + "..." : snippet;
    }
}