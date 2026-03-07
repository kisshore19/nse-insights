package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.entity.NseDailyPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CsvParserService {

    private static final DateTimeFormatter NSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public List<NseDailyPrice> parse(String csvContent, LocalDate tradeDate) {
        List<NseDailyPrice> result = new ArrayList<>();
        int skipped = 0;
        int processed = 0;
        boolean dateValidated = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                if (line.isBlank()) continue;

                String[] cols = line.split(",");
                if (cols.length < 15) {
                    log.warn("Skipping row with insufficient columns ({}): {}", cols.length, line);
                    skipped++;
                    continue;
                }

                String symbol  = cols[0].trim();
                String series  = cols[1].trim();
                String dateStr = cols[2].trim();

                if (!"EQ".equalsIgnoreCase(series)) continue;

                // ── Date Validation (once, from first EQ row) ─────────────────
                if (!dateValidated) {
                    LocalDate fileDate = parseFileDate(dateStr);
                    if (fileDate == null) {
                        throw new RuntimeException(
                                "Could not parse DATE1 from file: '" + dateStr + "'");
                    }
                    if (!fileDate.equals(tradeDate)) {
                        throw new RuntimeException(
                                "Date mismatch! Requested: " + tradeDate
                                        + " but file contains: " + fileDate
                                        + ". Please verify you are downloading the correct file.");
                    }
                    log.info("File date validated successfully: {} matches requested trade date", fileDate);
                    dateValidated = true;
                }

                try {
                    BigDecimal prevClose   = parseBD(cols[3]);
                    BigDecimal openPrice   = parseBD(cols[4]);
                    BigDecimal highPrice   = parseBD(cols[5]);
                    BigDecimal lowPrice    = parseBD(cols[6]);
                    BigDecimal closePrice  = parseBD(cols[8]);
                    long tradedQty         = parseLong(cols[10]);
                    BigDecimal turnover    = parseBD(cols[11]);
                    Long deliveryQty       = parseNullableLong(cols[13]);
                    BigDecimal deliveryPct = parseBD(cols[14]);

                    BigDecimal pctChange = null;
                    if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0
                            && closePrice != null) {
                        pctChange = closePrice.subtract(prevClose)
                                .divide(prevClose, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    result.add(NseDailyPrice.builder()
                            .tradeDate(tradeDate)
                            .symbol(symbol)
                            .series(series)
                            .openPrice(openPrice)
                            .highPrice(highPrice)
                            .lowPrice(lowPrice)
                            .closePrice(closePrice)
                            .prevClose(prevClose)
                            .pctChange(pctChange)
                            .tradedQuantity(tradedQty)
                            .turnover(turnover)
                            .deliveryQty(deliveryQty)
                            .deliveryPct(deliveryPct)
                            .build());

                    processed++;

                } catch (Exception ex) {
                    log.warn("Skipping malformed row for symbol '{}': {}", symbol, ex.getMessage());
                    skipped++;
                }
            }

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Bhavacopy CSV: {}", ex.getMessage(), ex);
            throw new RuntimeException("CSV parsing failed: " + ex.getMessage(), ex);
        }

        if (!dateValidated) {
            throw new RuntimeException(
                    "No EQ series records found for date: " + tradeDate
                            + ". This may be a market holiday or wrong file.");
        }

        log.info("Parsing complete — {} EQ records processed, {} rows skipped", processed, skipped);
        return result;
    }

    private LocalDate parseFileDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, NSE_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            log.error("Failed to parse date from file: '{}'", dateStr);
            return null;
        }
    }

    private BigDecimal parseBD(String val) {
        if (val == null || val.isBlank() || val.trim().equals("-")) return null;
        return new BigDecimal(val.trim());
    }

    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        return Long.parseLong(val.trim().replace(",", ""));
    }

    private Long parseNullableLong(String val) {
        if (val == null || val.isBlank() || val.trim().equals("-")) return null;
        try {
            return Long.parseLong(val.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}