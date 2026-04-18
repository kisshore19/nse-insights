package com.kisshore19.nseinsights.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for the single CTE stats query (findStatsForPeriod).
 * One row per symbol. periodKey is not in this projection — it is
 * added by the service when building the entity.
 */
public interface CandleStatsProjection {

    String     getSymbol();
    LocalDate  getCandleDate();      // MIN(trade_date) — first trading day
    LocalDate  getCandleEndDate();   // MAX(trade_date) — last  trading day

    // OHLC
    BigDecimal getOpenPrice();
    BigDecimal getHighPrice();
    BigDecimal getLowPrice();
    BigDecimal getLastPrice();       // COALESCE(last_price, close_price) of last day

    // High/Low dates and trend
    LocalDate  getHighPriceDate();   // earliest date where high_price = period MAX
    LocalDate  getLowPriceDate();    // earliest date where low_price  = period MIN
    String     getTrend();           // UP | DOWN | SIDE

    // Volume extremes
    Long       getHighVolQty();
    LocalDate  getHighVolDate();
    Long       getLowVolQty();
    LocalDate  getLowVolDate();

    // Delivery extremes
    Long       getHighDelivQty();
    LocalDate  getHighDelivDate();
    Long       getLowDelivQty();
    LocalDate  getLowDelivDate();
}