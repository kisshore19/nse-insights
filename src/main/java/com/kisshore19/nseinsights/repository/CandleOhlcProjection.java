package com.kisshore19.nseinsights.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Query 1 — period OHLC + boundaries.
 * Join key in Java: symbol + periodKey.
 */
public interface CandleOhlcProjection {
    String     getSymbol();
    String     getPeriodKey();      // "2024" / "2024-Q1" / "2024-01" / "2024-W03"
    LocalDate  getCandleDate();     // MIN(trade_date) — period start
    LocalDate  getCandleEndDate();  // MAX(trade_date) — period end
    BigDecimal getOpenPrice();      // open_price of first trading day
    BigDecimal getHighPrice();      // MAX(high_price) across all days
    BigDecimal getLowPrice();       // MIN(low_price)  across all days
    BigDecimal getLastPrice();      // COALESCE(last_price, close_price) of last trading day
}