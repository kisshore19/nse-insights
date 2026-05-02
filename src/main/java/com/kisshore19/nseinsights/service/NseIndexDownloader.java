package com.kisshore19.nseinsights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kisshore19.nseinsights.exception.NseUnavailableException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NseIndexDownloader {

    private final WebClient    nseWebClient;
    private final ObjectMapper objectMapper;

    @Value("${nse.index.storage-path}")
    private String storagePath;

    private static final String INDEX_BASE_URL =
            "https://archives.nseindia.com/content/indices/";

    // Indices that have constituent CSV files on NSE archives
    private static final Map<String, String> INDEX_FILES = Map.ofEntries(
            // ── Broad Market ──────────────────────────────────────────────────
            Map.entry("NIFTY50",             "ind_nifty50list.csv"),
            Map.entry("NIFTYNEXT50",         "ind_niftynext50list.csv"),
            Map.entry("NIFTY100",            "ind_nifty100list.csv"),
            Map.entry("NIFTY200",            "ind_nifty200list.csv"),
            Map.entry("NIFTY500",            "ind_nifty500list.csv"),
            Map.entry("NIFTYMIDCAP50",       "ind_niftymidcap50list.csv"),
            Map.entry("NIFTYMIDCAP100",      "ind_niftymidcap100list.csv"),
            Map.entry("NIFTYMIDCAP150",      "ind_niftymidcap150list.csv"),
            Map.entry("NIFTYSMALLCAP50",     "ind_niftysmallcap50list.csv"),
            Map.entry("NIFTYSMALLCAP100",    "ind_niftysmallcap100list.csv"),
            Map.entry("NIFTYSMALLCAP250",    "ind_niftysmallcap250list.csv"),
            Map.entry("NIFTYLARGEMIDCAP250", "ind_niftylargemidcap250list.csv"),
            // ── Sectoral ──────────────────────────────────────────────────────
            Map.entry("NIFTYIT",             "ind_niftyitlist.csv"),
            Map.entry("NIFTYBANK",           "ind_niftybanklist.csv"),
            Map.entry("NIFTYAUTO",           "ind_niftyautolist.csv"),
            Map.entry("NIFTYPHARMA",         "ind_niftypharmalist.csv"),
            Map.entry("NIFTYFMCG",           "ind_niftyfmcglist.csv"),
            Map.entry("NIFTYMETAL",          "ind_niftymetallist.csv"),
            Map.entry("NIFTYREALTY",         "ind_niftyrealtylist.csv"),
            Map.entry("NIFTYMEDIA",          "ind_niftymedialist.csv"),
            Map.entry("NIFTYENERGY",         "ind_niftyenergylist.csv"),
            Map.entry("NIFTYHEALTHCARE",     "ind_niftyhealthcarelist.csv"),
            Map.entry("NIFTYOILGAS",         "ind_niftyoilgaslist.csv"),
            Map.entry("NIFTYCONSUMERDURAB",  "ind_niftyconsumerdurableslist.csv"),
            Map.entry("NIFTYPRIVBANK",       "ind_nifty_privatebanklist.csv"),
            Map.entry("NIFTYPSUBANK",        "ind_niftypsubanklist.csv")
    );

    // Indices whose constituent files are absent from NSE archives — fetched via NSE live API.
    // Key = app index name, Value = NSE API index parameter (as shown in allIndices API)
    private static final Map<String, String> NSE_API_INDICES = Map.of(
            "NIFTYFINSERVICE", "NIFTY FIN SERVICE",
            "NIFTYCHEMICALS",  "NIFTY CHEMICALS"
    );

    private static final String NSE_API_URL =
            "https://www.nseindia.com/api/equity-stockIndices?index=";

    public static List<String> getSupportedIndexNames() {
        return Stream.concat(INDEX_FILES.keySet().stream(), NSE_API_INDICES.keySet().stream())
                .sorted().collect(Collectors.toList());
    }

    public IndexDownloadResult download(String indexName) {
        String upper = indexName.toUpperCase();

        if (NSE_API_INDICES.containsKey(upper)) {
            return downloadViaApi(upper);
        }

        String fileName = INDEX_FILES.get(upper);
        if (fileName == null) {
            throw new IllegalArgumentException(
                    "Unsupported index: " + indexName + ". Supported: " + getSupportedIndexNames());
        }

        String url      = INDEX_BASE_URL + fileName;
        Path   filePath = Paths.get(storagePath, fileName);

        if (Files.exists(filePath)) {
            log.info("Index file found on disk, skipping download: {}", filePath);
            try {
                String csvContent = Files.readString(filePath, StandardCharsets.UTF_8);
                return IndexDownloadResult.builder()
                        .indexName(upper)
                        .url(url)
                        .fileName(fileName)
                        .csvContent(csvContent)
                        .servedFromCache(true)
                        .build();
            } catch (IOException ex) {
                log.warn("Index file unreadable: {}. Re-downloading.", filePath);
            }
        }

        log.info("Downloading index {} from NSE: {}", upper, url);
        String csvContent = downloadFromNse(url, upper);
        saveToDisk(filePath, csvContent, upper);

        return IndexDownloadResult.builder()
                .indexName(upper)
                .url(url)
                .fileName(fileName)
                .csvContent(csvContent)
                .servedFromCache(false)
                .build();
    }

    // Fetch constituent list from NSE live equity-stockIndices API and convert to CSV format
    private IndexDownloadResult downloadViaApi(String upper) {
        String nseIndexName = NSE_API_INDICES.get(upper);
        String cacheFileName = "api_" + upper.toLowerCase() + "_list.csv";
        Path   filePath      = Paths.get(storagePath, cacheFileName);
        String apiUrl        = NSE_API_URL + nseIndexName.replace(" ", "%20");

        if (Files.exists(filePath)) {
            log.info("API-sourced index file found on disk, skipping download: {}", filePath);
            try {
                String csvContent = Files.readString(filePath, StandardCharsets.UTF_8);
                return IndexDownloadResult.builder()
                        .indexName(upper)
                        .url(apiUrl)
                        .fileName(cacheFileName)
                        .csvContent(csvContent)
                        .servedFromCache(true)
                        .build();
            } catch (IOException ex) {
                log.warn("Cached API file unreadable: {}. Re-fetching.", filePath);
            }
        }

        log.info("Fetching {} constituents via NSE API: {}", upper, apiUrl);
        String csvContent = fetchAndConvertFromApi(apiUrl, nseIndexName, upper);
        saveToDisk(filePath, csvContent, upper);

        return IndexDownloadResult.builder()
                .indexName(upper)
                .url(apiUrl)
                .fileName(cacheFileName)
                .csvContent(csvContent)
                .servedFromCache(false)
                .build();
    }

    private String fetchAndConvertFromApi(String apiUrl, String nseIndexName, String indexName) {
        try {
            String json = nseWebClient.get()
                    .uri(apiUrl)
                    .header("Referer", "https://www.nseindia.com")
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json == null || json.isBlank()) {
                throw new NseUnavailableException("Empty API response for index: " + indexName);
            }

            JsonNode root  = objectMapper.readTree(json);
            JsonNode data  = root.path("data");

            if (!data.isArray() || data.isEmpty()) {
                throw new NseUnavailableException(
                        "No constituent data in API response for: " + indexName);
            }

            // Convert JSON array → CSV (same format as IndexCsvParserService expects)
            // Format: Company Name,Industry,Symbol,Series,ISIN Code
            StringBuilder sb = new StringBuilder("Company Name,Industry,Symbol,Series,ISIN Code\n");
            int count = 0;
            for (JsonNode node : data) {
                String symbol = node.path("symbol").asText("").trim();
                // First row is the index itself — skip it
                if (symbol.equalsIgnoreCase(nseIndexName) || symbol.contains(" ")) continue;

                JsonNode meta    = node.path("meta");
                String company   = meta.path("companyName").asText("").replace(",", " ").trim();
                String industry  = meta.path("industry").asText("").replace(",", " ").trim();
                String series    = node.path("series").asText("EQ").trim();
                String isin      = meta.path("isin").asText("").trim();

                if (symbol.isBlank()) continue;

                sb.append(company).append(',')
                  .append(industry).append(',')
                  .append(symbol).append(',')
                  .append(series).append(',')
                  .append(isin).append('\n');
                count++;
            }

            log.info("API fetch complete for {} — {} constituents", indexName, count);
            return sb.toString();

        } catch (NseUnavailableException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            throw new NseUnavailableException(
                    "NSE API returned HTTP " + ex.getStatusCode().value()
                            + " for index: " + indexName, ex);
        } catch (Exception ex) {
            throw new NseUnavailableException(
                    "Failed to fetch index " + indexName + " from NSE API: " + ex.getMessage(), ex);
        }
    }

    public void invalidateCache(String indexName) {
        String upper = indexName.toUpperCase();

        String fileName;
        if (NSE_API_INDICES.containsKey(upper)) {
            fileName = "api_" + upper.toLowerCase() + "_list.csv";
        } else {
            fileName = INDEX_FILES.get(upper);
            if (fileName == null) return;
        }

        Path filePath = Paths.get(storagePath, fileName);
        try {
            if (Files.deleteIfExists(filePath)) {
                log.info("Deleted cached index file: {}", filePath);
            }
        } catch (IOException ex) {
            log.warn("Could not delete cached index file {}: {}", filePath, ex.getMessage());
        }
    }

    private String downloadFromNse(String url, String indexName) {
        try {
            String csvContent = nseWebClient.get()
                    .uri(url)
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (csvContent == null || csvContent.isBlank()) {
                throw new NseUnavailableException(
                        "Empty response from NSE for index: " + indexName);
            }
            log.info("Downloaded {} chars for index {}", csvContent.length(), indexName);
            return csvContent;

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new NseUnavailableException(
                        "Index file not found on NSE for: " + indexName);
            }
            throw new NseUnavailableException(
                    "NSE returned HTTP " + ex.getStatusCode().value()
                            + " for index: " + indexName, ex);
        } catch (NseUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NseUnavailableException(
                    "Failed to download index " + indexName + ": " + ex.getMessage(), ex);
        }
    }

    private void saveToDisk(Path filePath, String csvContent, String indexName) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, csvContent, StandardCharsets.UTF_8);
            log.info("Index file saved to disk: {}", filePath);
        } catch (IOException ex) {
            log.warn("Failed to save index file for {}: {}", indexName, ex.getMessage());
        }
    }

    @Getter
    @Builder
    public static class IndexDownloadResult {
        private final String  indexName;
        private final String  url;
        private final String  fileName;
        private final String  csvContent;
        private final boolean servedFromCache;
    }
}
