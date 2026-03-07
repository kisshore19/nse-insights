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
import java.util.*;

@Service
@Slf4j
public class CsvParserService {

    // NSE CSV uses format: 30-Jan-2026
    private static final DateTimeFormatter NSE_CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    /**
     * Parses actual NSE Bhavacopy CSV format.
     * Delivery data (DELIV_QTY, DELIV_PER) comes directly from the CSV.
     *
     * Actual NSE CSV Format (as of 2026):
     * SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE,
     * LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS,
     * NO_OF_TRADES, DELIV_QTY, DELIV_PER
     *
     * Note: TURNOVER_LACS is in Lakhs (multiply by 100000 to get actual rupees)
     */
    public List<NseDailyPrice> parseAndMerge(
            String bhavatopyCsv,
            String mtoContent,  // Not used - kept for backward compatibility
            LocalDate tradeDate) {

        List<NseDailyPrice> result = new ArrayList<>();
        int lineNum = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(bhavatopyCsv))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // Skip empty lines
                if (line.isBlank()) continue;

                String[] cols = line.split(",");

                // Trim all columns to remove spaces
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim();
                }

                // Skip header - identify by looking for "SYMBOL" in first column
                if (firstLine) {
                    firstLine = false;
                    if (cols[0].equalsIgnoreCase("SYMBOL")) {
                        log.info("CSV header found at line {}: {} columns", lineNum, cols.length);
                        continue;
                    }
                }

                // Require minimum 15 columns for complete data
                if (cols.length < 15) {
                    log.debug("Line {} has only {} columns (need 15), skipping", lineNum, cols.length);
                    continue;
                }

                try {
                    // Parse columns based on NSE CSV format (0-indexed)
                    // 0: SYMBOL, 1: SERIES, 2: DATE1, 3: PREV_CLOSE, 4: OPEN_PRICE
                    // 5: HIGH_PRICE, 6: LOW_PRICE, 7: LAST_PRICE, 8: CLOSE_PRICE
                    // 9: AVG_PRICE, 10: TTL_TRD_QNTY, 11: TURNOVER_LACS, 12: NO_OF_TRADES
                    // 13: DELIV_QTY, 14: DELIV_PER

                    String symbol = cols[0];
                    String series = cols[1];
                    String dateStr = cols[2];

                    // Only process EQ series
                    if (!"EQ".equalsIgnoreCase(series)) {
                        continue;
                    }

                    // Parse date from CSV format (30-Jan-2026)
                    LocalDate csvTradeDate;
                    try {
                        csvTradeDate = LocalDate.parse(dateStr, NSE_CSV_DATE_FORMAT);
                    } catch (Exception ex) {
                        log.warn("Line {}: Failed to parse date '{}' for symbol {}: {}", lineNum, dateStr, symbol, ex.getMessage());
                        continue;
                    }

                    // Parse price data
                    BigDecimal prevClose = parseBD(cols[3]);
                    BigDecimal open = parseBD(cols[4]);
                    BigDecimal high = parseBD(cols[5]);
                    BigDecimal low = parseBD(cols[6]);
                    BigDecimal close = parseBD(cols[8]);  // Skip LAST_PRICE at cols[7]
                    long tradedQty = parseLong(cols[10]);

                    // Parse turnover (in Lakhs - convert to actual rupees)
                    BigDecimal turnoverLacs = parseBD(cols[11]);
                    BigDecimal turnover = (turnoverLacs != null)
                            ? turnoverLacs.multiply(BigDecimal.valueOf(100000)).setScale(2, RoundingMode.HALF_UP)
                            : null;

                    // Parse delivery data (directly from CSV)
                    Long deliveryQty = parseLongNullable(cols[13]);
                    BigDecimal deliveryPct = parseBD(cols[14]);

                    // Validate required fields
                    if (close == null || close.compareTo(BigDecimal.ZERO) <= 0) {
                        log.debug("Line {}: Invalid close price {} for symbol {}, skipping", lineNum, cols[8], symbol);
                        continue;
                    }

                    // Calculate % change
                    BigDecimal pctChange = null;
                    if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                        pctChange = close.subtract(prevClose)
                                .divide(prevClose, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    // Build and add entity
                    NseDailyPrice entity = NseDailyPrice.builder()
                            .tradeDate(tradeDate != null ? tradeDate : csvTradeDate)
                            .symbol(symbol)
                            .series(series)
                            .openPrice(open)
                            .highPrice(high)
                            .lowPrice(low)
                            .closePrice(close)
                            .prevClose(prevClose)
                            .pctChange(pctChange)
                            .tradedQuantity(tradedQty)
                            .turnover(turnover)
                            .deliveryQty(deliveryQty)
                            .deliveryPct(deliveryPct)
                            .build();

                    result.add(entity);

                } catch (Exception ex) {
                    log.debug("Line {}: Exception parsing row for symbol {}: {}", lineNum, cols.length > 0 ? cols[0] : "UNKNOWN", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse Bhavacopy CSV: {}", ex.getMessage(), ex);
            throw new RuntimeException("CSV parsing failed: " + ex.getMessage(), ex);
        }

        log.info("Successfully parsed {} EQ records from Bhavacopy (processed {} lines total)", result.size(), lineNum);
        return result;
    }

    // ...existing code...

    private BigDecimal parseBD(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return new BigDecimal(val.trim());
        } catch (Exception ex) {
            log.debug("Failed to parse BigDecimal: {}", val);
            return null;
        }
    }

    private long parseLong(String val) {
        if (val == null || val.isBlank()) return 0L;
        try {
            return Long.parseLong(val.trim().replace(",", ""));
        } catch (Exception ex) {
            log.debug("Failed to parse Long: {}", val);
            return 0L;
        }
    }

    private Long parseLongNullable(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Long.parseLong(val.trim().replace(",", ""));
        } catch (Exception ex) {
            log.debug("Failed to parse nullable Long: {}", val);
            return null;
        }
    }
}
