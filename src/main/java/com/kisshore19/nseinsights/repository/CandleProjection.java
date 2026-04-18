package com.kisshore19.nseinsights.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring Data projection interface.
 * Maps directly from native SQL query result columns → Java getters.
 * Zero JVM aggregation — everything computed at DB level.
 */
public interface CandleProjection {

    String    getSymbol();
    String    getTimeframe();
    LocalDate getCandleDate();
    LocalDate getCandleEndDate();

    // OHLC
    BigDecimal getOpenPrice();
    BigDecimal getHighPrice();
    BigDecimal getLowPrice();
    BigDecimal getClosePrice();

    // Direction & Trend
    // MySQL IF() returns 0/1 (Long/Integer), not Boolean — use Integer here
    // Convert to Boolean in CandleService.toEntity()
    Integer   getIsGreen();
    LocalDate getHighDate();
    LocalDate getLowDate();
    String    getTrend();

    // Volume
    Long      getTotalVolume();
    LocalDate getMaxVolumeDate();
    Long      getMaxVolumeDayQty();

    // Delivery
    Long      getTotalDelivery();
    LocalDate getMaxDeliveryDate();
    Long      getMaxDeliveryDayQty();

    Integer   getTradingDays();
}