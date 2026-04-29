package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.IndexCandleStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface IndexCandleStatsRepository
        extends JpaRepository<IndexCandleStats, Long>, JpaSpecificationExecutor<IndexCandleStats> {

    // ── Single record lookup ──────────────────────────────────────────────────
    @Query("""
            SELECT s FROM IndexCandleStats s
            WHERE s.indexName = :indexName
              AND s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    Optional<IndexCandleStats> findByIndexNameTimeframeAndPeriodKey(
            @Param("indexName")  String indexName,
            @Param("timeframe")  String timeframe,
            @Param("periodKey")  String periodKey);

    // ── Skip logic — which indices already have stats for this period ─────────
    @Query("""
            SELECT s.indexName FROM IndexCandleStats s
            WHERE s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    Set<String> findExistingIndicesForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Available periods ─────────────────────────────────────────────────────
    @Query("""
            SELECT s.periodKey           AS periodKey,
                   MIN(s.candleDate)     AS candleDate,
                   MAX(s.candleEndDate)  AS candleEndDate
            FROM IndexCandleStats s
            WHERE s.timeframe = :timeframe
            GROUP BY s.periodKey
            ORDER BY s.periodKey ASC
            """)
    List<CandlePeriodProjection> findAvailablePeriods(@Param("timeframe") String timeframe);

    // ── Delete ────────────────────────────────────────────────────────────────
    @Modifying
    @Query("""
            DELETE FROM IndexCandleStats s
            WHERE s.timeframe = :timeframe
              AND s.periodKey = :periodKey
            """)
    void deleteByTimeframeAndPeriodKey(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Main CTE stats query on index_daily_close ─────────────────────────────
    // Computes OHLC, trend, high/low dates, volume extremes, and avg fundamentals
    // for every index in the given date range. One row per index.
    @Query(value = """
        WITH period_data AS (
            SELECT
                index_name,
                trade_date,
                open_value,
                high_value,
                low_value,
                close_value,
                volume,
                pe,
                pb,
                div_yield
            FROM index_daily_close
            WHERE trade_date BETWEEN :fromDate AND :toDate
        ),
        enriched AS (
            SELECT
                index_name,
                trade_date,
                open_value,
                high_value,
                low_value,
                close_value,
                volume,
                pe,
                pb,
                div_yield,
                MIN(trade_date)   OVER w AS period_start,
                MAX(trade_date)   OVER w AS period_end,
                MAX(high_value)   OVER w AS period_high,
                MIN(low_value)    OVER w AS period_low,
                MAX(volume)       OVER w AS max_vol,
                MIN(volume)       OVER w AS min_vol
            FROM period_data
            WINDOW w AS (PARTITION BY index_name)
        ),
        aggregated AS (
            SELECT
                index_name,
                period_start                                                                AS candle_date,
                period_end                                                                  AS candle_end_date,

                MIN(CASE WHEN trade_date = period_start THEN open_value   END)             AS open_value,
                MAX(high_value)                                                             AS high_value,
                MIN(low_value)                                                              AS low_value,
                MIN(CASE WHEN trade_date = period_end   THEN close_value  END)             AS last__value,

                MIN(CASE WHEN high_value = period_high  THEN trade_date   END)             AS high_value_date,
                MIN(CASE WHEN low_value  = period_low   THEN trade_date   END)             AS low_value_date,

                MAX(volume)                                                                 AS high_vol_qty,
                MIN(CASE WHEN volume = max_vol THEN trade_date END)                        AS high_vol_date,
                MIN(volume)                                                                 AS low_vol_qty,
                MIN(CASE WHEN volume = min_vol THEN trade_date END)                        AS low_vol_date,

                ROUND(AVG(pe),        2)                                                    AS avg_pe,
                ROUND(AVG(pb),        2)                                                    AS avg_pb,
                ROUND(AVG(div_yield), 2)                                                    AS avg_div_yield
            FROM enriched
            GROUP BY index_name, period_start, period_end
        )
        SELECT
            index_name                                              AS indexName,
            candle_date                                             AS candleDate,
            candle_end_date                                         AS candleEndDate,
            open_value                                              AS openValue,
            high_value                                              AS highValue,
            low_value                                               AS lowValue,
            last__value                                              AS lastValue,
            high_value_date                                         AS highValueDate,
            low_value_date                                          AS lowValueDate,
            CASE
                WHEN low_value_date  < high_value_date THEN 'UP'
                WHEN high_value_date < low_value_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                     AS trend,
            high_vol_qty                                            AS highVolQty,
            high_vol_date                                           AS highVolDate,
            low_vol_qty                                             AS lowVolQty,
            low_vol_date                                            AS lowVolDate,
            avg_pe                                                  AS avgPe,
            avg_pb                                                  AS avgPb,
            avg_div_yield                                           AS avgDivYield
        FROM aggregated
        ORDER BY index_name ASC
        """, nativeQuery = true)
    List<IndexCandleStatsProjection> findIndexStatsForPeriod(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate")   LocalDate toDate);

    // ── Distinct period keys that exist in index_daily_close ──────────────────
    @Query(value = """
        SELECT DISTINCT
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                    LPAD(WEEK(trade_date, 1), 2, '0'))
            END AS period_key
        FROM index_daily_close
        ORDER BY period_key ASC
        """, nativeQuery = true)
    List<String> findDistinctPeriodKeys(@Param("timeframe") String timeframe);
}
