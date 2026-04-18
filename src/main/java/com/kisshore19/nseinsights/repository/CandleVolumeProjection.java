package com.kisshore19.nseinsights.repository;

import java.time.LocalDate;

/**
 * Query 3 — highest and lowest single-day volume within the period.
 * Join key in Java: symbol + periodKey.
 * Tie-breaking: earliest trade_date among tied days.
 */
public interface CandleVolumeProjection {
    String    getSymbol();
    String    getPeriodKey();      // join key
    Long      getHighVolQty();     // MAX(traded_quantity)
    LocalDate getHighVolDate();    // earliest date of max volume day
    Long      getLowVolQty();      // MIN(traded_quantity)
    LocalDate getLowVolDate();     // earliest date of min volume day
}