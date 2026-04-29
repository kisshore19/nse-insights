package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.entity.IndexMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses NSE index constituent CSV files.
 *
 * Expected format (from archives.nseindia.com/content/indices/):
 *   Company Name,Industry,Symbol,Series,ISIN Code
 *   Adani Enterprises Limited,METALS & MINING,ADANIENT,EQ,INE423A01024
 */
@Service
@Slf4j
public class IndexCsvParserService {

    public List<IndexMaster> parse(String csvContent, String indexName) {
        List<IndexMaster> result  = new ArrayList<>();
        int processed = 0;
        int skipped   = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String  line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }   // skip header
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 5) {
                    log.warn("[{}] Skipping short row (cols={}): {}", indexName, cols.length, line);
                    skipped++;
                    continue;
                }

                try {
                    String companyName = cols[0].trim();
                    String industry    = cols[1].trim();
                    String symbol      = cols[2].trim();
                    String isin        = cols[4].trim();

                    // Skip footer notes or blank symbols
                    if (symbol.isBlank() || symbol.equalsIgnoreCase("Symbol")
                            || !symbol.matches("[A-Z0-9&._-]{1,20}")) {
                        skipped++;
                        continue;
                    }

                    result.add(IndexMaster.builder()
                            .indexName(indexName)
                            .symbol(symbol)
                            .companyName(companyName.isEmpty() ? null : companyName)
                            .sector(industry.isEmpty() ? null : industry)
                            .isin(isin.isEmpty() ? null : isin)
                            .isActive(true)
                            .addedDate(LocalDate.now())
                            .build());

                    processed++;
                } catch (Exception ex) {
                    log.warn("[{}] Skipping malformed row: {}", indexName, ex.getMessage());
                    skipped++;
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Index CSV parsing failed for " + indexName + ": " + ex.getMessage(), ex);
        }

        log.info("[{}] Parse complete — {} records, {} skipped", indexName, processed, skipped);
        return result;
    }
}
