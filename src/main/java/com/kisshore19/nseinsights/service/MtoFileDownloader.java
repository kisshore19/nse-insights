package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.exception.NseUnavailableException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class MtoFileDownloader {

    private final WebClient nseWebClient;

    @Value("${nse.base-url}")
    private String nseBaseUrl;

    // MTO filename format: MTO_15012025.DAT
    private static final DateTimeFormatter MTO_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyyyy");

    public MtoResult download(LocalDate tradeDate) {
        String dateStr = tradeDate.format(MTO_DATE_FORMAT); // 15012025
        String fileName = "MTO_" + dateStr + ".DAT";

        String url = nseBaseUrl + "/content/historical/EQUITIES/"
                + tradeDate.getYear() + "/"
                + tradeDate.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase()
                + "/" + fileName;

        log.info("Downloading MTO file from: {}", url);

        try {
            String mtoContent = nseWebClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (mtoContent == null || mtoContent.isBlank()) {
                log.warn("MTO file empty or not available for {}. Delivery data will be null.", tradeDate);
                return MtoResult.builder()
                        .url(url)
                        .fileName(fileName)
                        .mtoContent("")
                        .build();
            }

            return MtoResult.builder()
                    .url(url)
                    .fileName(fileName)
                    .mtoContent(mtoContent)
                    .build();

        } catch (Exception ex) {
            // MTO is non-critical — log warning but don't fail the whole download
            log.warn("Could not download MTO file for {}. Delivery data will be unavailable. Error: {}",
                    tradeDate, ex.getMessage());
            return MtoResult.builder()
                    .url(url)
                    .fileName(fileName)
                    .mtoContent("")
                    .build();
        }
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────
    @Getter
    @Builder
    public static class MtoResult {
        private final String url;
        private final String fileName;
        private final String mtoContent;
    }
}
