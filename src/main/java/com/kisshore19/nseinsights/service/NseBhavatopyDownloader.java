package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.exception.NseUnavailableException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NseBhavatopyDownloader {

    private final WebClient nseWebClient;

    @Value("${nse.base-url}")
    private String nseBaseUrl;

    // NSE Bhavacopy date format: 15-Jan-2025
    private static final DateTimeFormatter NSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    // NSE Bhavacopy filename format: cm15JAN2025bhav.csv
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMMyyyy");

    public BhavatopyResult download(LocalDate tradeDate) {
        String dateStr    = tradeDate.format(NSE_DATE_FORMAT);        // 15-Jan-2025
        String fileDateStr = tradeDate.format(FILE_DATE_FORMAT).toUpperCase(); // 15JAN2025
        String expectedFileName = "cm" + fileDateStr + "bhav.csv";

        // Try multiple URL formats:
        // 1. New API format
        // 2. Historical archives ZIP format
        // 3. Direct CSV download format

        String[] urls = {
            // Format 1: Direct CSV (for recent dates)
            nseBaseUrl + "/content/historical/EQUITIES/"
                + tradeDate.getYear() + "/"
                + tradeDate.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase()
                + "/" + expectedFileName,

            // Format 2: ZIP format (for some dates)
            nseBaseUrl + "/content/historical/EQUITIES/"
                + tradeDate.getYear() + "/"
                + tradeDate.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase()
                + "/" + expectedFileName + ".zip",

            // Format 3: Older archive format
            "https://archives.nseindia.com/products/content/sec_bhavdata_full_"
                + String.format("%02d%02d%04d", tradeDate.getDayOfMonth(),
                    tradeDate.getMonthValue(), tradeDate.getYear()) + ".csv"
        };

        for (String url : urls) {
            try {
                log.info("Attempting to download Bhavacopy from: {}", url);

                byte[] response = nseWebClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();

                if (response == null || response.length == 0) {
                    log.warn("Empty response from: {}", url);
                    continue;
                }

                // Try to extract as ZIP first
                if (isZipFile(response)) {
                    String csvContent = extractCsvFromZip(response);
                    return BhavatopyResult.builder()
                            .url(url)
                            .fileName(expectedFileName)
                            .csvContent(csvContent)
                            .build();
                } else {
                    // Assume it's plain CSV
                    String csvContent = new String(response);
                    if (csvContent.length() > 100) {
                        return BhavatopyResult.builder()
                                .url(url)
                                .fileName(expectedFileName)
                                .csvContent(csvContent)
                                .build();
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to download from {}: {}", url, ex.getMessage());
                // Try next URL
            }
        }

        throw new NseUnavailableException(
                "Could not download Bhavacopy for " + tradeDate + " from any NSE source");
    }

    private boolean isZipFile(byte[] data) {
        return data.length >= 4
            && data[0] == 0x50 && data[1] == 0x4B
            && data[2] == 0x03 && data[3] == 0x04;
    }

    private String extractCsvFromZip(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".csv")) {
                    return new String(zis.readAllBytes());
                }
            }
        }
        throw new NseUnavailableException("No CSV file found inside downloaded ZIP");
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────
    @Getter
    @Builder
    public static class BhavatopyResult {
        private final String url;
        private final String fileName;
        private final String csvContent;
    }
}
