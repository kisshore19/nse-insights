package com.kisshore19.nseinsights.repository;

import java.time.LocalDate;

/**
 * Projection for available period keys in candle_stats.
 * Used by CandleStatsRepository.findAvailablePeriods().
 */
public interface CandlePeriodProjection {
    String    getPeriodKey();
    LocalDate getCandleDate();
    LocalDate getCandleEndDate();
}