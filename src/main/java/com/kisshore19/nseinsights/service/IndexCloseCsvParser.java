package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.entity.IndexDailyClose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses NSE ind_close_all_DDMMYYYY.csv
 *
 * Columns: Index Name, Index Date, Open Index Value, High Index Value,
 *          Low Index Value, Closing Index Value, Points Change, Change(%),
 *          Volume, Turnover (Rs. Cr.), P/E, P/B, Div Yield
 *
 * Date format in file: dd-MM-yyyy  (e.g. 28-04-2026)
 * Change(%): may omit leading zero (e.g. -.4 instead of -0.4)
 */
@Service
@Slf4j
public class IndexCloseCsvParser {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    public List<IndexDailyClose> parse(String csvContent, LocalDate expectedDate) {
        List<IndexDailyClose> result  = new ArrayList<>();
        int processed = 0;
        int skipped   = 0;
        boolean dateValidated = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String  line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }   // skip header
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 6) {
                    log.warn("Skipping short row (cols={}): {}", cols.length, line);
                    skipped++;
                    continue;
                }

                try {
                    String indexName = cols[0].trim();
                    String dateStr   = cols[1].trim();

                    if (indexName.isBlank()) { skipped++; continue; }

                    // Validate file date against expected date (once)
                    if (!dateValidated) {
                        LocalDate fileDate = parseDate(dateStr);
                        if (fileDate == null) {
                            throw new RuntimeException(
                                    "Cannot parse date from file: '" + dateStr + "'");
                        }
                        if (!fileDate.equals(expectedDate)) {
                            throw new RuntimeException(
                                    "Date mismatch — expected: " + expectedDate
                                            + " but file contains: " + fileDate);
                        }
                        dateValidated = true;
                    }

                    result.add(IndexDailyClose.builder()
                            .indexName(indexName)
                            .tradeDate(expectedDate)
                            .openValue(parseBD(cols[2]))
                            .highValue(parseBD(cols[3]))
                            .lowValue(parseBD(cols[4]))
                            .closeValue(parseBD(cols[5]))
                            .pointsChange(cols.length > 6  ? parseBD(cols[6])  : null)
                            .pctChange(   cols.length > 7  ? parseBD(cols[7])  : null)
                            .volume(      cols.length > 8  ? parseLong(cols[8]) : null)
                            .turnover(    cols.length > 9  ? parseBD(cols[9])  : null)
                            .pe(          cols.length > 10 ? parseBD(cols[10]) : null)
                            .pb(          cols.length > 11 ? parseBD(cols[11]) : null)
                            .divYield(    cols.length > 12 ? parseBD(cols[12]) : null)
                            .build());

                    processed++;
                } catch (Exception ex) {
                    log.warn("Skipping malformed row: {} — {}", line, ex.getMessage());
                    skipped++;
                }
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Index close CSV parsing failed: " + ex.getMessage(), ex);
        }

        if (!dateValidated) {
            throw new RuntimeException(
                    "No data rows found for date: " + expectedDate
                            + ". Market holiday or empty file?");
        }

        log.info("Index close parse complete — {} records, {} skipped", processed, skipped);
        return result;
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return LocalDate.parse(val.trim(), FILE_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            log.error("Failed to parse date: '{}'", val);
            return null;
        }
    }

    private BigDecimal parseBD(String val) {
        if (val == null || val.isBlank() || val.trim().equals("-")) return null;
        String clean = val.trim();
        // Handle values like "-.4" → "-0.4" so BigDecimal parses correctly
        if (clean.startsWith("-.")) clean = "-0" + clean.substring(1);
        else if (clean.startsWith(".")) clean = "0" + clean;
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String val) {
        if (val == null || val.isBlank() || val.trim().equals("-")) return null;
        try {
            return Long.parseLong(val.trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
