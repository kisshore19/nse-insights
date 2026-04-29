package com.kisshore19.nseinsights.service;

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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NseIndexDownloader {

    private final WebClient nseWebClient;

    @Value("${nse.index.storage-path}")
    private String storagePath;

    private static final String INDEX_BASE_URL =
            "https://archives.nseindia.com/content/indices/";

    // Index name → NSE filename mapping
    private static final Map<String, String> INDEX_FILES = Map.ofEntries(
            Map.entry("NIFTY50",           "ind_nifty50list.csv"),
            Map.entry("NIFTYNEXT50",       "ind_niftynext50list.csv"),
            Map.entry("NIFTY100",          "ind_nifty100list.csv"),
            Map.entry("NIFTY200",          "ind_nifty200list.csv"),
            Map.entry("NIFTY500",          "ind_nifty500list.csv"),
            Map.entry("NIFTYMIDCAP50",     "ind_niftymidcap50list.csv"),
            Map.entry("NIFTYMIDCAP100",    "ind_niftymidcap100list.csv"),
            Map.entry("NIFTYMIDCAP150",    "ind_niftymidcap150list.csv"),
            Map.entry("NIFTYSMALLCAP50",   "ind_niftysmallcap50list.csv"),
            Map.entry("NIFTYSMALLCAP100",  "ind_niftysmallcap100list.csv"),
            Map.entry("NIFTYSMALLCAP250",  "ind_niftysmallcap250list.csv"),
            Map.entry("NIFTYLARGEMIDCAP250", "ind_niftylargemidcap250list.csv")
    );

    public static List<String> getSupportedIndexNames() {
        return INDEX_FILES.keySet().stream().sorted().toList();
    }

    public IndexDownloadResult download(String indexName) {
        String upper = indexName.toUpperCase();
        String fileName = INDEX_FILES.get(upper);
        if (fileName == null) {
            throw new IllegalArgumentException(
                    "Unsupported index: " + indexName + ". Supported: " + getSupportedIndexNames());
        }

        String url      = INDEX_BASE_URL + fileName;
        Path   filePath = Paths.get(storagePath, fileName);

        // Check local disk cache
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

    public void invalidateCache(String indexName) {
        String upper    = indexName.toUpperCase();
        String fileName = INDEX_FILES.get(upper);
        if (fileName == null) return;

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
