package com.kisshore19.nseinsights.repository;

import java.time.LocalDate;

/**
 * Query 4 — highest and lowest single-day delivery within the period.
 * Join key in Java: symbol + periodKey.
 * Tie-breaking: earliest trade_date among tied days.
 */
public interface CandleDeliveryProjection {
    String    getSymbol();
    String    getPeriodKey();        // join key
    Long      getHighDelivQty();     // MAX(delivery_qty)
    LocalDate getHighDelivDate();    // earliest date of max delivery day
    Long      getLowDelivQty();      // MIN(delivery_qty)
    LocalDate getLowDelivDate();     // earliest date of min delivery day
}