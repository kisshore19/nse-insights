package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.StockCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {

    // ── Fetch all candles for symbol + timeframe ───────────────────────────
    List<StockCandle> findBySymbolAndTimeframeOrderByCandleDateAsc(
            String symbol, String timeframe);

    // ── Fetch candles within a date range ─────────────────────────────────
    // Uses candle_date >= from AND candle_date <= to
    // For MONTH timeframe: candle_date is always the 1st of month (2025-01-01, 2025-02-01 ...)
    // So passing from=2025-01-01 to=2025-12-31 correctly captures all 12 months
    @Query("""
            SELECT c FROM StockCandle c
            WHERE c.symbol    = :symbol
              AND c.timeframe = :timeframe
              AND c.candleDate >= :from
              AND c.candleDate <= :to
            ORDER BY c.candleDate ASC
            """)
    List<StockCandle> findBySymbolAndTimeframeAndDateRange(
            @Param("symbol")    String symbol,
            @Param("timeframe") String timeframe,
            @Param("from")      LocalDate from,
            @Param("to")        LocalDate to);

    // ── Latest candle for symbol + timeframe ──────────────────────────────
    Optional<StockCandle> findTopBySymbolAndTimeframeOrderByCandleDateDesc(
            String symbol, String timeframe);

    // ── Previous candle before a date ─────────────────────────────────────
    Optional<StockCandle> findTopBySymbolAndTimeframeAndCandleDateBeforeOrderByCandleDateDesc(
            String symbol, String timeframe, LocalDate date);

    // ── Delete for rebuild ─────────────────────────────────────────────────
    @Modifying
    @Query("DELETE FROM StockCandle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    int deleteBySymbolAndTimeframe(@Param("symbol") String symbol,
                                   @Param("timeframe") String timeframe);

    @Modifying
    @Query("DELETE FROM StockCandle c WHERE c.timeframe = :timeframe")
    int deleteByTimeframe(@Param("timeframe") String timeframe);

    // ── Existence check ───────────────────────────────────────────────────
    boolean existsBySymbolAndTimeframeAndCandleDate(
            String symbol, String timeframe, LocalDate candleDate);

    // ── Stats ─────────────────────────────────────────────────────────────
    long countByTimeframe(String timeframe);

    @Query("SELECT DISTINCT c.symbol FROM StockCandle c WHERE c.timeframe = :timeframe")
    List<String> findDistinctSymbolsByTimeframe(@Param("timeframe") String timeframe);
}