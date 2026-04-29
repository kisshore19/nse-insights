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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NseIndexCloseDownloader {

    private final WebClient nseWebClient;

    @Value("${nse.index.storage-path}")
    private String storagePath;

    // URL: https://nsearchives.nseindia.com/content/indices/ind_close_all_28042026.csv
    private static final String BASE_URL =
            "https://nsearchives.nseindia.com/content/indices/";

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyyyy");

    public IndexCloseResult download(LocalDate tradeDate) {
        String dateStr  = tradeDate.format(FILE_DATE_FORMAT);    // 28042026
        String fileName = "ind_close_all_" + dateStr + ".csv";
        String url      = BASE_URL + fileName;
        Path   filePath = Paths.get(storagePath, fileName);

        // ── Check local disk cache ─────────────────────────────────────────────
        if (Files.exists(filePath)) {
            log.info("Index close file found on disk: {}", filePath);
            try {
                String csvContent = Files.readString(filePath, StandardCharsets.UTF_8);
                return IndexCloseResult.builder()
                        .url(url).fileName(fileName).csvContent(csvContent)
                        .servedFromCache(true).build();
            } catch (IOException ex) {
                log.warn("File unreadable: {}. Re-downloading.", filePath);
            }
        }

        // ── Download from NSE ──────────────────────────────────────────────────
        log.info("Downloading index close data for {} from: {}", tradeDate, url);
        String csvContent = downloadFromNse(url, tradeDate);
        saveToDisk(filePath, csvContent, tradeDate);

        return IndexCloseResult.builder()
                .url(url).fileName(fileName).csvContent(csvContent)
                .servedFromCache(false).build();
    }

    private String downloadFromNse(String url, LocalDate tradeDate) {
        try {
            String csvContent = nseWebClient.get()
                    .uri(url)
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (csvContent == null || csvContent.isBlank()) {
                throw new NseUnavailableException(
                        "Empty response from NSE for date: " + tradeDate);
            }
            log.info("Downloaded {} chars for index close {}", csvContent.length(), tradeDate);
            return csvContent;

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new NseUnavailableException(
                        "Index close file not found on NSE for: " + tradeDate
                                + ". Possibly a market holiday or weekend.");
            }
            throw new NseUnavailableException(
                    "NSE returned HTTP " + ex.getStatusCode().value()
                            + " for date: " + tradeDate, ex);
        } catch (NseUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NseUnavailableException(
                    "Failed to download index close for " + tradeDate
                            + ": " + ex.getMessage(), ex);
        }
    }

    private void saveToDisk(Path filePath, String csvContent, LocalDate tradeDate) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, csvContent, StandardCharsets.UTF_8);
            log.info("Index close file saved: {}", filePath);
        } catch (IOException ex) {
            log.warn("Failed to save index close file for {}: {}", tradeDate, ex.getMessage());
        }
    }

    @Getter
    @Builder
    public static class IndexCloseResult {
        private final String  url;
        private final String  fileName;
        private final String  csvContent;
        private final boolean servedFromCache;
    }
}
