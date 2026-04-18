package com.kisshore19.nseinsights.repository;

import java.time.LocalDate;

/**
 * Query 2 — date of period high, date of period low, trend.
 * Join key in Java: symbol + periodKey.
 */
public interface CandleHighLowProjection {
    String    getSymbol();
    String    getPeriodKey();   // join key
    LocalDate getHighDate();    // earliest date where high_price = period MAX
    LocalDate getLowDate();     // earliest date where low_price  = period MIN
    String    getTrend();       // UP | DOWN | SIDE
}