package com.kisshore19.nseinsights.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for the index CTE stats query (findIndexStatsForPeriod).
 * One row per index name. periodKey is added by the service when building the entity.
 */
public interface IndexCandleStatsProjection {

    String     getIndexName();
    LocalDate  getCandleDate();       // MIN(trade_date) — first trading day
    LocalDate  getCandleEndDate();    // MAX(trade_date) — last  trading day

    // OHLC
    BigDecimal getOpenValue();
    BigDecimal getHighValue();
    BigDecimal getLowValue();
    BigDecimal getLastValue();        // close_value of last trading day

    // High/Low dates and trend
    LocalDate  getHighValueDate();    // earliest date where high_value = period MAX
    LocalDate  getLowValueDate();     // earliest date where low_value  = period MIN
    String     getTrend();            // UP | DOWN | SIDE

    // Volume extremes
    Long       getHighVolQty();
    LocalDate  getHighVolDate();
    Long       getLowVolQty();
    LocalDate  getLowVolDate();

    // Period-average fundamentals
    BigDecimal getAvgPe();
    BigDecimal getAvgPb();
    BigDecimal getAvgDivYield();
}
