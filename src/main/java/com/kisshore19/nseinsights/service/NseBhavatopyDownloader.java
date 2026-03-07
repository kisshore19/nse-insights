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
public class NseBhavatopyDownloader {

    private final WebClient nseWebClient;

    @Value("${nse.bhavdata.storage-path}")
    private String storagePath;

    // NSE URL date format: ddMMyyyy → 05032025
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyyyy");

    public BhavatopyResult download(LocalDate tradeDate) {
        String dateStr  = tradeDate.format(FILE_DATE_FORMAT);       // 05032025
        String fileName = "sec_bhavdata_full_" + dateStr + ".csv";
        String url      = "https://archives.nseindia.com/products/content/" + fileName;

        Path filePath = Paths.get(storagePath, fileName);

        // ── Step 1: Check local disk first ────────────────────────────────────
        if (Files.exists(filePath)) {
            log.info("File found on disk, skipping NSE download: {}", filePath);
            try {
                String csvContent = Files.readString(filePath, StandardCharsets.UTF_8);
                return BhavatopyResult.builder()
                        .url(url)
                        .fileName(fileName)
                        .filePath(filePath.toString())
                        .csvContent(csvContent)
                        .servedFromCache(true)
                        .build();
            } catch (IOException ex) {
                // File exists but can't be read — log warning and re-download
                log.warn("File exists on disk but failed to read: {}. Re-downloading from NSE.", filePath);
            }
        }

        // ── Step 2: Download from NSE ──────────────────────────────────────────
        log.info("File not on disk. Downloading from NSE: {}", url);
        String csvContent = downloadFromNse(url, tradeDate);

        // ── Step 3: Save to local disk ─────────────────────────────────────────
        saveToDisk(filePath, csvContent, tradeDate);

        return BhavatopyResult.builder()
                .url(url)
                .fileName(fileName)
                .filePath(filePath.toString())
                .csvContent(csvContent)
                .servedFromCache(false)
                .build();
    }

    // ── Download from NSE ──────────────────────────────────────────────────────
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

            log.info("Downloaded {} chars from NSE for {}", csvContent.length(), tradeDate);
            return csvContent;

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new NseUnavailableException(
                        "File not found on NSE for date: " + tradeDate
                                + ". Possibly a market holiday or weekend.");
            }
            throw new NseUnavailableException(
                    "NSE returned HTTP " + ex.getStatusCode().value()
                            + " for date: " + tradeDate, ex);
        } catch (NseUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NseUnavailableException(
                    "Failed to download from NSE for date: " + tradeDate
                            + " — " + ex.getMessage(), ex);
        }
    }

    // ── Save CSV to local disk ─────────────────────────────────────────────────
    private void saveToDisk(Path filePath, String csvContent, LocalDate tradeDate) {
        try {
            // Create directories if they don't exist
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, csvContent, StandardCharsets.UTF_8);
            log.info("File saved to disk: {}", filePath);
        } catch (IOException ex) {
            // Non-fatal — data is still in memory, DB save can proceed
            log.warn("Failed to save file to disk: {}. Continuing with DB insert. Error: {}",
                    filePath, ex.getMessage());
        }
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────
    @Getter
    @Builder
    public static class BhavatopyResult {
        private final String url;
        private final String fileName;
        private final String filePath;
        private final String csvContent;
        private final boolean servedFromCache;  // true = read from disk, false = downloaded from NSE
    }
}