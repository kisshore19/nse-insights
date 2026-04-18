package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.dto.response.CandleStatsAvailableResponse.PeriodEntry;
import com.kisshore19.nseinsights.entity.CandleStats;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CandleStatsRepository extends JpaRepository<CandleStats, Long>, JpaSpecificationExecutor<CandleStats> {

    // ── Read — single record ──────────────────────────────────────────────────

    @Query("""
            SELECT s FROM CandleStats s
            WHERE s.symbol    = :symbol
              AND s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    Optional<CandleStats> findBySymbolTimeframeAndPeriodKey(
            @Param("symbol")    String symbol,
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Skip logic — which symbols already have stats for this period ─────────
    // Used before running the 4 queries so we only process missing symbols.

    @Query("""
            SELECT s.symbol FROM CandleStats s
            WHERE s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    Set<String> findExistingSymbolsForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Available periods — for the /available endpoint ───────────────────────
    // Groups by period_key only — one row per period regardless of how many
    // symbols have slightly different candle_date values within the same period.
    // MIN(candle_date) = first trading day, MAX(candle_end_date) = last trading day.

    @Query("""
            SELECT s.periodKey        AS periodKey,
                   MIN(s.candleDate)  AS candleDate,
                   MAX(s.candleEndDate) AS candleEndDate
            FROM CandleStats s
            WHERE s.timeframe = :timeframe
            GROUP BY s.periodKey
            ORDER BY s.periodKey ASC
            """)
    List<CandlePeriodProjection> findAvailablePeriods(@Param("timeframe") String timeframe);

    // ── Delete — only used when explicitly wiping a specific period ───────────

    @Modifying
    @Query("""
            DELETE FROM CandleStats s
            WHERE s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    void deleteByTimeframeAndPeriodKey(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    @Modifying
    @Query("DELETE FROM CandleStats s WHERE s.timeframe = :timeframe")
    void deleteByTimeframe(@Param("timeframe") String timeframe);
}