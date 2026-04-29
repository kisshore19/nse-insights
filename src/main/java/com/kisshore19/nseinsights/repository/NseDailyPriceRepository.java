package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.NseDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NseDailyPriceRepository extends JpaRepository<NseDailyPrice, Long> {

    // ── Existence & counts ────────────────────────────────────────────────────
    boolean existsByTradeDate(LocalDate tradeDate);

    @Modifying
    @Query("DELETE FROM NseDailyPrice p WHERE p.tradeDate = :tradeDate")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Query("SELECT COUNT(DISTINCT p.tradeDate) FROM NseDailyPrice p")
    long countDistinctTradeDates();

    @Query("SELECT DISTINCT p.tradeDate FROM NseDailyPrice p ORDER BY p.tradeDate DESC")
    List<LocalDate> findAllDistinctTradeDates();

    @Query("SELECT MAX(p.tradeDate) FROM NseDailyPrice p")
    Optional<LocalDate> findLatestTradeDate();

    @Query("SELECT MIN(p.tradeDate) FROM NseDailyPrice p")
    Optional<LocalDate> findOldestTradeDate();

    long countByTradeDate(LocalDate tradeDate);

    List<NseDailyPrice> findBySymbolOrderByTradeDateAsc(String symbol);

    List<NseDailyPrice> findByTradeDateOrderByPctChangeDesc(LocalDate tradeDate);

    Optional<NseDailyPrice> findByTradeDateAndSymbol(LocalDate tradeDate, String symbol);

    // ── Top Gainers / Losers ──────────────────────────────────────────────────
    @Query(value = """
            SELECT * FROM nse_daily_price
            WHERE trade_date = :tradeDate AND pct_change IS NOT NULL
            ORDER BY pct_change DESC LIMIT :limit
            """, nativeQuery = true)
    List<NseDailyPrice> findTopGainers(@Param("tradeDate") LocalDate tradeDate,
                                       @Param("limit")     int limit);

    @Query(value = """
            SELECT * FROM nse_daily_price
            WHERE trade_date = :tradeDate AND pct_change IS NOT NULL
            ORDER BY pct_change ASC LIMIT :limit
            """, nativeQuery = true)
    List<NseDailyPrice> findTopLosers(@Param("tradeDate") LocalDate tradeDate,
                                      @Param("limit")     int limit);

    // ── Index-filtered Top Gainers / Losers ───────────────────────────────────
    @Query(value = """
            SELECT * FROM nse_daily_price
            WHERE trade_date = :tradeDate AND symbol IN :symbols AND pct_change IS NOT NULL
            ORDER BY pct_change DESC LIMIT :limit
            """, nativeQuery = true)
    List<NseDailyPrice> findTopGainersForSymbols(@Param("tradeDate") LocalDate tradeDate,
                                                 @Param("symbols")   List<String> symbols,
                                                 @Param("limit")     int limit);

    @Query(value = """
            SELECT * FROM nse_daily_price
            WHERE trade_date = :tradeDate AND symbol IN :symbols AND pct_change IS NOT NULL
            ORDER BY pct_change ASC LIMIT :limit
            """, nativeQuery = true)
    List<NseDailyPrice> findTopLosersForSymbols(@Param("tradeDate") LocalDate tradeDate,
                                                @Param("symbols")   List<String> symbols,
                                                @Param("limit")     int limit);

    // All stocks for a date filtered by symbol list (for index-based stock view)
    @Query("SELECT p FROM NseDailyPrice p WHERE p.tradeDate = :tradeDate AND p.symbol IN :symbols ORDER BY p.pctChange DESC")
    List<NseDailyPrice> findByTradeDateAndSymbolIn(@Param("tradeDate") LocalDate tradeDate,
                                                   @Param("symbols")   List<String> symbols);

    // =========================================================================
    // SINGLE SYMBOL — DAY
    // =========================================================================
    @Query(value = """
        SELECT
            p.symbol                                            AS symbol,
            'DAY'                                               AS timeframe,
            p.trade_date                                        AS candleDate,
            p.trade_date                                        AS candleEndDate,
            p.open_price                                        AS openPrice,
            p.high_price                                        AS highPrice,
            p.low_price                                         AS lowPrice,
            COALESCE(p.last_price, p.close_price)               AS closePrice,
            IF(COALESCE(p.last_price, p.close_price)
               > p.open_price, 1, 0)                           AS isGreen,
            p.trade_date                                        AS highDate,
            p.trade_date                                        AS lowDate,
            'SIDE'                                              AS trend,
            p.traded_quantity                                   AS totalVolume,
            p.trade_date                                        AS maxVolumeDate,
            p.traded_quantity                                   AS maxVolumeDayQty,
            p.delivery_qty                                      AS totalDelivery,
            p.trade_date                                        AS maxDeliveryDate,
            p.delivery_qty                                      AS maxDeliveryDayQty,
            1                                                   AS tradingDays
        FROM nse_daily_price p
        WHERE p.symbol = :symbol
          AND p.trade_date BETWEEN :from AND :to
        ORDER BY p.trade_date ASC
        """, nativeQuery = true)
    List<CandleProjection> buildDayCandles(
            @Param("symbol") String symbol,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);

    // =========================================================================
    // SINGLE SYMBOL — WEEK
    // Step 1 (period_bounds): group rows into weekly buckets → MIN/MAX date, H/L, vol, delivery
    // Step 2 (ohlc):          join back to get open (first day) and close (last day)
    // Step 3 (final SELECT):  compute isGreen, trend and emit final columns
    // =========================================================================
  /*  @Query(value = """
                """, nativeQuery = true)
    List<CandleProjection> buildWeekCandles(
            @Param("symbol") String symbol,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);*/

    // =========================================================================
    // SINGLE SYMBOL — MONTH
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                DATE_FORMAT(trade_date, '%Y-%m-01')  AS period_key,
                MIN(trade_date)      AS period_start,
                MAX(trade_date)      AS period_end,
                MAX(high_price)      AS period_high,
                MIN(low_price)       AS period_low,
                SUM(traded_quantity) AS total_volume,
                MAX(traded_quantity) AS max_volume,
                SUM(delivery_qty)    AS total_delivery,
                MAX(delivery_qty)    AS max_delivery,
                COUNT(*)             AS trading_days
            FROM nse_daily_price
            WHERE symbol = :symbol
              AND trade_date BETWEEN :from AND :to
            GROUP BY symbol, DATE_FORMAT(trade_date, '%Y-%m-01')
        ),
        ohlc AS (
            SELECT
                pb.*,
                first_day.open_price                                AS open_price,
                COALESCE(last_day.last_price, last_day.close_price) AS close_price,
                high_day.trade_date                                 AS high_date,
                low_day.trade_date                                  AS low_date,
                vol_day.trade_date                                  AS max_vol_date,
                deliv_day.trade_date                                AS max_deliv_date
            FROM period_bounds pb
            JOIN nse_daily_price first_day
              ON first_day.symbol = pb.symbol AND first_day.trade_date = pb.period_start
            JOIN nse_daily_price last_day
              ON last_day.symbol  = pb.symbol AND last_day.trade_date  = pb.period_end
            JOIN nse_daily_price high_day
              ON high_day.symbol  = pb.symbol AND high_day.high_price  = pb.period_high
             AND high_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price low_day
              ON low_day.symbol   = pb.symbol AND low_day.low_price    = pb.period_low
             AND low_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price vol_day
              ON vol_day.symbol   = pb.symbol AND vol_day.traded_quantity = pb.max_volume
             AND vol_day.trade_date BETWEEN pb.period_start AND pb.period_end
            LEFT JOIN nse_daily_price deliv_day
              ON deliv_day.symbol = pb.symbol AND deliv_day.delivery_qty  = pb.max_delivery
             AND deliv_day.trade_date BETWEEN pb.period_start AND pb.period_end
        )
        SELECT
            symbol                                              AS symbol,
            'MONTH'                                             AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM ohlc
        ORDER BY period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildMonthCandles(
            @Param("symbol") String symbol,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);

    // =========================================================================
    // SINGLE SYMBOL — QUARTER
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                MAKEDATE(YEAR(trade_date),
                    1 + (QUARTER(trade_date)-1)*91 -
                    DAYOFYEAR(DATE_FORMAT(trade_date,'%Y-01-01')) + 1) AS period_key,
                MIN(trade_date)      AS period_start,
                MAX(trade_date)      AS period_end,
                MAX(high_price)      AS period_high,
                MIN(low_price)       AS period_low,
                SUM(traded_quantity) AS total_volume,
                MAX(traded_quantity) AS max_volume,
                SUM(delivery_qty)    AS total_delivery,
                MAX(delivery_qty)    AS max_delivery,
                COUNT(*)             AS trading_days
            FROM nse_daily_price
            WHERE symbol = :symbol
              AND trade_date BETWEEN :from AND :to
            GROUP BY symbol,
                     MAKEDATE(YEAR(trade_date),
                         1 + (QUARTER(trade_date)-1)*91 -
                         DAYOFYEAR(DATE_FORMAT(trade_date,'%Y-01-01')) + 1)
        ),
        ohlc AS (
            SELECT
                pb.*,
                first_day.open_price                                AS open_price,
                COALESCE(last_day.last_price, last_day.close_price) AS close_price,
                high_day.trade_date                                 AS high_date,
                low_day.trade_date                                  AS low_date,
                vol_day.trade_date                                  AS max_vol_date,
                deliv_day.trade_date                                AS max_deliv_date
            FROM period_bounds pb
            JOIN nse_daily_price first_day
              ON first_day.symbol = pb.symbol AND first_day.trade_date = pb.period_start
            JOIN nse_daily_price last_day
              ON last_day.symbol  = pb.symbol AND last_day.trade_date  = pb.period_end
            JOIN nse_daily_price high_day
              ON high_day.symbol  = pb.symbol AND high_day.high_price  = pb.period_high
             AND high_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price low_day
              ON low_day.symbol   = pb.symbol AND low_day.low_price    = pb.period_low
             AND low_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price vol_day
              ON vol_day.symbol   = pb.symbol AND vol_day.traded_quantity = pb.max_volume
             AND vol_day.trade_date BETWEEN pb.period_start AND pb.period_end
            LEFT JOIN nse_daily_price deliv_day
              ON deliv_day.symbol = pb.symbol AND deliv_day.delivery_qty  = pb.max_delivery
             AND deliv_day.trade_date BETWEEN pb.period_start AND pb.period_end
        )
        SELECT
            symbol                                              AS symbol,
            'QUARTER'                                           AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM ohlc
        ORDER BY period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildQuarterCandles(
            @Param("symbol") String symbol,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);

    // =========================================================================
    // SINGLE SYMBOL — YEAR
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                DATE_FORMAT(trade_date, '%Y-01-01')  AS period_key,
                MIN(trade_date)      AS period_start,
                MAX(trade_date)      AS period_end,
                MAX(high_price)      AS period_high,
                MIN(low_price)       AS period_low,
                SUM(traded_quantity) AS total_volume,
                MAX(traded_quantity) AS max_volume,
                SUM(delivery_qty)    AS total_delivery,
                MAX(delivery_qty)    AS max_delivery,
                COUNT(*)             AS trading_days
            FROM nse_daily_price
            WHERE symbol = :symbol
              AND trade_date BETWEEN :from AND :to
            GROUP BY symbol, DATE_FORMAT(trade_date, '%Y-01-01')
        ),
        ohlc AS (
            SELECT
                pb.*,
                first_day.open_price                                AS open_price,
                COALESCE(last_day.last_price, last_day.close_price) AS close_price,
                high_day.trade_date                                 AS high_date,
                low_day.trade_date                                  AS low_date,
                vol_day.trade_date                                  AS max_vol_date,
                deliv_day.trade_date                                AS max_deliv_date
            FROM period_bounds pb
            JOIN nse_daily_price first_day
              ON first_day.symbol = pb.symbol AND first_day.trade_date = pb.period_start
            JOIN nse_daily_price last_day
              ON last_day.symbol  = pb.symbol AND last_day.trade_date  = pb.period_end
            JOIN nse_daily_price high_day
              ON high_day.symbol  = pb.symbol AND high_day.high_price  = pb.period_high
             AND high_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price low_day
              ON low_day.symbol   = pb.symbol AND low_day.low_price    = pb.period_low
             AND low_day.trade_date BETWEEN pb.period_start AND pb.period_end
            JOIN nse_daily_price vol_day
              ON vol_day.symbol   = pb.symbol AND vol_day.traded_quantity = pb.max_volume
             AND vol_day.trade_date BETWEEN pb.period_start AND pb.period_end
            LEFT JOIN nse_daily_price deliv_day
              ON deliv_day.symbol = pb.symbol AND deliv_day.delivery_qty  = pb.max_delivery
             AND deliv_day.trade_date BETWEEN pb.period_start AND pb.period_end
        )
        SELECT
            symbol                                              AS symbol,
            'YEAR'                                              AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM ohlc
        ORDER BY period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildYearCandles(
            @Param("symbol") String symbol,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to);

    // =========================================================================
    // ALL SYMBOLS — DAY
    // =========================================================================
    @Query(value = """
        SELECT
            symbol                                              AS symbol,
            'DAY'                                               AS timeframe,
            trade_date                                          AS candleDate,
            trade_date                                          AS candleEndDate,
            open_price                                          AS openPrice,
            high_price                                          AS highPrice,
            low_price                                           AS lowPrice,
            COALESCE(last_price, close_price)                   AS closePrice,
            IF(COALESCE(last_price, close_price)
               > open_price, 1, 0)                             AS isGreen,
            trade_date                                          AS highDate,
            trade_date                                          AS lowDate,
            'SIDE'                                              AS trend,
            traded_quantity                                     AS totalVolume,
            trade_date                                          AS maxVolumeDate,
            traded_quantity                                     AS maxVolumeDayQty,
            delivery_qty                                        AS totalDelivery,
            trade_date                                          AS maxDeliveryDate,
            delivery_qty                                        AS maxDeliveryDayQty,
            1                                                   AS tradingDays
        FROM nse_daily_price
        ORDER BY symbol ASC, trade_date ASC
        """, nativeQuery = true)
    List<CandleProjection> buildAllDayCandles();

    // =========================================================================
    // ALL SYMBOLS — WEEK
    // Step 1 (period_bounds): window function per symbol+week → period extremes as columns
    // Step 2 (aggregated):    GROUP BY to collapse to one row per symbol+period
    // Step 3 (final SELECT):  derive isGreen and trend cleanly
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            -- Step 1: tag every row with its period extremes using window functions
            --         (no GROUP BY yet — each row keeps its own trade_date)
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                close_price,
                last_price,
                traded_quantity,
                delivery_qty,
                DATE_SUB(trade_date, INTERVAL (WEEKDAY(trade_date)) DAY) AS period_key,
                MIN(trade_date)      OVER w AS period_start,
                MAX(trade_date)      OVER w AS period_end,
                MAX(high_price)      OVER w AS period_high,
                MIN(low_price)       OVER w AS period_low,
                MAX(traded_quantity) OVER w AS max_vol,
                MAX(delivery_qty)    OVER w AS max_deliv
            FROM nse_daily_price
            WINDOW w AS (PARTITION BY symbol,
                         DATE_SUB(trade_date, INTERVAL (WEEKDAY(trade_date)) DAY))
        ),
        aggregated AS (
            -- Step 2: collapse to one row per symbol+period, pick open/close/dates
            SELECT
                symbol,
                period_key,
                period_start,
                period_end,
                MAX(high_price)                                                    AS period_high,
                MIN(low_price)                                                     AS period_low,
                MIN(CASE WHEN trade_date = period_start THEN open_price    END)   AS open_price,
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)            END)   AS close_price,
                MIN(CASE WHEN high_price = period_high THEN trade_date     END)   AS high_date,
                MIN(CASE WHEN low_price  = period_low  THEN trade_date     END)   AS low_date,
                SUM(traded_quantity)                                               AS total_volume,
                MAX(traded_quantity)                                               AS max_volume,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date  END)   AS max_vol_date,
                SUM(delivery_qty)                                                  AS total_delivery,
                MAX(delivery_qty)                                                  AS max_delivery,
                MIN(CASE WHEN delivery_qty   = max_deliv THEN trade_date   END)   AS max_deliv_date,
                COUNT(*)                                                           AS trading_days
            FROM period_bounds
            GROUP BY symbol, period_key, period_start, period_end
        )
        -- Step 3: final output — isGreen and trend are trivial here
        SELECT
            symbol                                              AS symbol,
            'WEEK'                                              AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM aggregated
        ORDER BY symbol ASC, period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildAllWeekCandles();

    // =========================================================================
    // ALL SYMBOLS — MONTH
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                close_price,
                last_price,
                traded_quantity,
                delivery_qty,
                DATE_FORMAT(trade_date, '%Y-%m-01')  AS period_key,
                MIN(trade_date)      OVER w AS period_start,
                MAX(trade_date)      OVER w AS period_end,
                MAX(high_price)      OVER w AS period_high,
                MIN(low_price)       OVER w AS period_low,
                MAX(traded_quantity) OVER w AS max_vol,
                MAX(delivery_qty)    OVER w AS max_deliv
            FROM nse_daily_price
            WINDOW w AS (PARTITION BY symbol, DATE_FORMAT(trade_date, '%Y-%m-01'))
        ),
        aggregated AS (
            SELECT
                symbol,
                period_key,
                period_start,
                period_end,
                MAX(high_price)                                                    AS period_high,
                MIN(low_price)                                                     AS period_low,
                MIN(CASE WHEN trade_date = period_start THEN open_price    END)   AS open_price,
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)            END)   AS close_price,
                MIN(CASE WHEN high_price = period_high THEN trade_date     END)   AS high_date,
                MIN(CASE WHEN low_price  = period_low  THEN trade_date     END)   AS low_date,
                SUM(traded_quantity)                                               AS total_volume,
                MAX(traded_quantity)                                               AS max_volume,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date  END)   AS max_vol_date,
                SUM(delivery_qty)                                                  AS total_delivery,
                MAX(delivery_qty)                                                  AS max_delivery,
                MIN(CASE WHEN delivery_qty   = max_deliv THEN trade_date   END)   AS max_deliv_date,
                COUNT(*)                                                           AS trading_days
            FROM period_bounds
            GROUP BY symbol, period_key, period_start, period_end
        )
        SELECT
            symbol                                              AS symbol,
            'MONTH'                                             AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM aggregated
        ORDER BY symbol ASC, period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildAllMonthCandles();

    // =========================================================================
    // ALL SYMBOLS — QUARTER
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                close_price,
                last_price,
                traded_quantity,
                delivery_qty,
                MAKEDATE(YEAR(trade_date),
                    1 + (QUARTER(trade_date)-1)*91 -
                    DAYOFYEAR(DATE_FORMAT(trade_date,'%Y-01-01')) + 1) AS period_key,
                MIN(trade_date)      OVER w AS period_start,
                MAX(trade_date)      OVER w AS period_end,
                MAX(high_price)      OVER w AS period_high,
                MIN(low_price)       OVER w AS period_low,
                MAX(traded_quantity) OVER w AS max_vol,
                MAX(delivery_qty)    OVER w AS max_deliv
            FROM nse_daily_price
            WINDOW w AS (PARTITION BY symbol,
                         MAKEDATE(YEAR(trade_date),
                             1 + (QUARTER(trade_date)-1)*91 -
                             DAYOFYEAR(DATE_FORMAT(trade_date,'%Y-01-01')) + 1))
        ),
        aggregated AS (
            SELECT
                symbol,
                period_key,
                period_start,
                period_end,
                MAX(high_price)                                                    AS period_high,
                MIN(low_price)                                                     AS period_low,
                MIN(CASE WHEN trade_date = period_start THEN open_price    END)   AS open_price,
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)            END)   AS close_price,
                MIN(CASE WHEN high_price = period_high THEN trade_date     END)   AS high_date,
                MIN(CASE WHEN low_price  = period_low  THEN trade_date     END)   AS low_date,
                SUM(traded_quantity)                                               AS total_volume,
                MAX(traded_quantity)                                               AS max_volume,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date  END)   AS max_vol_date,
                SUM(delivery_qty)                                                  AS total_delivery,
                MAX(delivery_qty)                                                  AS max_delivery,
                MIN(CASE WHEN delivery_qty   = max_deliv THEN trade_date   END)   AS max_deliv_date,
                COUNT(*)                                                           AS trading_days
            FROM period_bounds
            GROUP BY symbol, period_key, period_start, period_end
        )
        SELECT
            symbol                                              AS symbol,
            'QUARTER'                                           AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM aggregated
        ORDER BY symbol ASC, period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildAllQuarterCandles();

    // =========================================================================
    // ALL SYMBOLS — YEAR
    // =========================================================================
    @Query(value = """
        WITH period_bounds AS (
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                close_price,
                last_price,
                traded_quantity,
                delivery_qty,
                DATE_FORMAT(trade_date, '%Y-01-01')  AS period_key,
                MIN(trade_date)      OVER w AS period_start,
                MAX(trade_date)      OVER w AS period_end,
                MAX(high_price)      OVER w AS period_high,
                MIN(low_price)       OVER w AS period_low,
                MAX(traded_quantity) OVER w AS max_vol,
                MAX(delivery_qty)    OVER w AS max_deliv
            FROM nse_daily_price
            WINDOW w AS (PARTITION BY symbol, DATE_FORMAT(trade_date, '%Y-01-01'))
        ),
        aggregated AS (
            SELECT
                symbol,
                period_key,
                period_start,
                period_end,
                MAX(high_price)                                                    AS period_high,
                MIN(low_price)                                                     AS period_low,
                MIN(CASE WHEN trade_date = period_start THEN open_price    END)   AS open_price,
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)            END)   AS close_price,
                MIN(CASE WHEN high_price = period_high THEN trade_date     END)   AS high_date,
                MIN(CASE WHEN low_price  = period_low  THEN trade_date     END)   AS low_date,
                SUM(traded_quantity)                                               AS total_volume,
                MAX(traded_quantity)                                               AS max_volume,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date  END)   AS max_vol_date,
                SUM(delivery_qty)                                                  AS total_delivery,
                MAX(delivery_qty)                                                  AS max_delivery,
                MIN(CASE WHEN delivery_qty   = max_deliv THEN trade_date   END)   AS max_deliv_date,
                COUNT(*)                                                           AS trading_days
            FROM period_bounds
            GROUP BY symbol, period_key, period_start, period_end
        )
        SELECT
            symbol                                              AS symbol,
            'YEAR'                                              AS timeframe,
            period_start                                        AS candleDate,
            period_end                                          AS candleEndDate,
            open_price                                          AS openPrice,
            period_high                                         AS highPrice,
            period_low                                          AS lowPrice,
            close_price                                         AS closePrice,
            IF(close_price > open_price, 1, 0)                 AS isGreen,
            high_date                                           AS highDate,
            low_date                                            AS lowDate,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend,
            total_volume                                        AS totalVolume,
            max_vol_date                                        AS maxVolumeDate,
            max_volume                                          AS maxVolumeDayQty,
            total_delivery                                      AS totalDelivery,
            max_deliv_date                                      AS maxDeliveryDate,
            max_delivery                                        AS maxDeliveryDayQty,
            trading_days                                        AS tradingDays
        FROM aggregated
        ORDER BY symbol ASC, period_start ASC
        """, nativeQuery = true)
    List<CandleProjection> buildAllYearCandles();


    // =========================================================================
    // CANDLE STATS — fresh from nse_daily_price, no dependency on stock_candle
    // =========================================================================

    /**
     * Computes candle stats for ALL symbols for the YEAR timeframe directly
     * from raw nse_daily_price rows.
     *
     * Each CTE does one job:
     *
     *   period_bounds — window functions tag every row with its year's extremes
     *                   (period_start, period_end, max/min vol, max/min deliv)
     *                   No GROUP BY yet — every row keeps its own trade_date.
     *
     *   aggregated    — GROUP BY collapses to one row per symbol+year.
     *                   Picks open (first day), last_price (last day),
     *                   high/low dates, and all extreme dates.
     *                   Tie-breaking for min vol/deliv: MIN(trade_date).
     *
     *   Final SELECT  — derives trend from high_date vs low_date.
     *                   Pure column aliasing, no logic.
     *
     * Add this method to NseDailyPriceRepository.
     */
    @Query(value = """
        WITH period_bounds AS (
            -- Step 1: tag every row with its year's period extremes
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                last_price,
                close_price,
                traded_quantity,
                delivery_qty,
                DATE_FORMAT(trade_date, '%Y-01-01')  AS period_key,
                MIN(trade_date)       OVER w          AS period_start,
                MAX(trade_date)       OVER w          AS period_end,
                MAX(high_price)       OVER w          AS period_high,
                MIN(low_price)        OVER w          AS period_low,
                MAX(traded_quantity)  OVER w          AS max_vol,
                MIN(traded_quantity)  OVER w          AS min_vol,
                MAX(delivery_qty)     OVER w          AS max_deliv,
                MIN(delivery_qty)     OVER w          AS min_deliv
            FROM nse_daily_price
            WINDOW w AS (PARTITION BY symbol, DATE_FORMAT(trade_date, '%Y-01-01'))
        ),
        aggregated AS (
            -- Step 2: collapse to one row per symbol+year
            --         MIN(CASE WHEN ...) picks the right value for each extreme
            --         Tie-breaking on min dates: MIN(trade_date) among tied rows
            SELECT
                symbol,
                period_key,
                period_start                                                          AS candle_date,
                period_end                                                            AS candle_end_date,

                -- Open: first trading day's open price
                MIN(CASE WHEN trade_date = period_start
                         THEN open_price                                        END)  AS open_price,

                -- High / Low for the year
                MAX(high_price)                                                       AS high_price,
                MIN(low_price)                                                        AS low_price,

                -- Last price: COALESCE(last_price, close_price) of final trading day
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)                 END)  AS last_price,

                -- High price date: earliest date where high_price = period_high
                MIN(CASE WHEN high_price = period_high THEN trade_date          END)  AS high_date,

                -- Low price date: earliest date where low_price = period_low
                MIN(CASE WHEN low_price  = period_low  THEN trade_date          END)  AS low_date,

                -- Highest volume
                MAX(traded_quantity)                                                   AS high_vol_qty,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date       END)  AS high_vol_date,

                -- Lowest volume (tie-break: earliest date)
                MIN(traded_quantity)                                                   AS low_vol_qty,
                MIN(CASE WHEN traded_quantity = min_vol   THEN trade_date       END)  AS low_vol_date,

                -- Highest delivery
                MAX(delivery_qty)                                                      AS high_deliv_qty,
                MIN(CASE WHEN delivery_qty    = max_deliv THEN trade_date       END)  AS high_deliv_date,

                -- Lowest delivery (tie-break: earliest date)
                MIN(delivery_qty)                                                      AS low_deliv_qty,
                MIN(CASE WHEN delivery_qty    = min_deliv THEN trade_date       END)  AS low_deliv_date

            FROM period_bounds
            GROUP BY symbol, period_key, period_start, period_end
        )
        -- Step 3: final output — trend derived from high_date vs low_date
        SELECT
            symbol                                                AS symbol,
            candle_date                                           AS candleDate,
            candle_end_date                                       AS candleEndDate,
            open_price                                            AS openPrice,
            high_price                                            AS highPrice,
            low_price                                             AS lowPrice,
            last_price                                            AS lastPrice,
            CASE
                WHEN low_date  < high_date THEN 'UP'
                WHEN high_date < low_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                   AS trend,
            high_vol_qty                                          AS highVolQty,
            high_vol_date                                         AS highVolDate,
            low_vol_qty                                           AS lowVolQty,
            low_vol_date                                          AS lowVolDate,
            high_deliv_qty                                        AS highDelivQty,
            high_deliv_date                                       AS highDelivDate,
            low_deliv_qty                                         AS lowDelivQty,
            low_deliv_date                                        AS lowDelivDate
        FROM aggregated
        ORDER BY symbol ASC, candle_date ASC
        """, nativeQuery = true)
    List<CandleStatsProjection> findStatsForAllSymbols();


    @Query(value = """
        SELECT
            p.symbol                                        AS symbol,
            YEAR(p.trade_date)                              AS year,
            MIN(p.trade_date)                               AS candleDate,
            MAX(p.trade_date)                               AS candleEndDate,
            MAX(p.high_price)                               AS highPrice,
            MIN(p.low_price)                                AS lowPrice,
            -- open: join back to get first day's open_price
            (SELECT p2.open_price
             FROM nse_daily_price p2
             WHERE p2.symbol     = p.symbol
               AND p2.trade_date = MIN(p.trade_date)
             LIMIT 1)                                       AS openPrice,
            -- last price: join back to get last day's last_price
            (SELECT COALESCE(p3.last_price, p3.close_price)
             FROM nse_daily_price p3
             WHERE p3.symbol     = p.symbol
               AND p3.trade_date = MAX(p.trade_date)
             LIMIT 1)                                       AS lastPrice
        FROM nse_daily_price p
        GROUP BY p.symbol, YEAR(p.trade_date)
        ORDER BY p.symbol ASC, YEAR(p.trade_date) ASC
        """, nativeQuery = true)
    List<CandleOhlcProjection> findOhlcStatsForAllSymbols();

    // ── Query 2: High date, Low date, Trend ───────────────────────────────────
    // Finds per symbol+year:
    //   high_date = earliest trade_date where high_price = yearly MAX(high_price)
    //   low_date  = earliest trade_date where low_price  = yearly MIN(low_price)
    //   trend     = UP if low_date < high_date, DOWN if high_date < low_date, else SIDE
    @Query(value = """
        SELECT
            agg.symbol                                      AS symbol,
            agg.yr                                          AS year,
            -- high_date: earliest day that hit the yearly high
            (SELECT MIN(h.trade_date)
             FROM nse_daily_price h
             WHERE h.symbol     = agg.symbol
               AND YEAR(h.trade_date) = agg.yr
               AND h.high_price = agg.period_high)         AS highDate,
            -- low_date: earliest day that hit the yearly low
            (SELECT MIN(l.trade_date)
             FROM nse_daily_price l
             WHERE l.symbol     = agg.symbol
               AND YEAR(l.trade_date) = agg.yr
               AND l.low_price  = agg.period_low)          AS lowDate,
            -- trend derived from high_date vs low_date
            CASE
                WHEN (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND YEAR(l.trade_date) = agg.yr
                        AND l.low_price = agg.period_low)
                   < (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND YEAR(h.trade_date) = agg.yr
                        AND h.high_price = agg.period_high)
                THEN 'UP'
                WHEN (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND YEAR(h.trade_date) = agg.yr
                        AND h.high_price = agg.period_high)
                   < (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND YEAR(l.trade_date) = agg.yr
                        AND l.low_price = agg.period_low)
                THEN 'DOWN'
                ELSE 'SIDE'
            END                                             AS trend
        FROM (
            SELECT
                symbol,
                YEAR(trade_date)    AS yr,
                MAX(high_price)     AS period_high,
                MIN(low_price)      AS period_low
            FROM nse_daily_price
            GROUP BY symbol, YEAR(trade_date)
        ) agg
        ORDER BY agg.symbol ASC, agg.yr ASC
        """, nativeQuery = true)
    List<CandleHighLowProjection> findHighLowStatsForAllSymbols();

    // ── Query 3: Volume extremes ──────────────────────────────────────────────
    // Finds per symbol+year:
    //   high_vol_qty  = MAX(traded_quantity)
    //   high_vol_date = earliest trade_date of that max volume day
    //   low_vol_qty   = MIN(traded_quantity)
    //   low_vol_date  = earliest trade_date of that min volume day (tie-break)
    @Query(value = """
        SELECT
            agg.symbol                                      AS symbol,
            agg.yr                                          AS year,
            agg.high_vol_qty                                AS highVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol            = agg.symbol
               AND YEAR(v.trade_date)  = agg.yr
               AND v.traded_quantity   = agg.high_vol_qty) AS highVolDate,
            agg.low_vol_qty                                 AS lowVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol            = agg.symbol
               AND YEAR(v.trade_date)  = agg.yr
               AND v.traded_quantity   = agg.low_vol_qty)  AS lowVolDate
        FROM (
            SELECT
                symbol,
                YEAR(trade_date)         AS yr,
                MAX(traded_quantity)     AS high_vol_qty,
                MIN(traded_quantity)     AS low_vol_qty
            FROM nse_daily_price
            GROUP BY symbol, YEAR(trade_date)
        ) agg
        ORDER BY agg.symbol ASC, agg.yr ASC
        """, nativeQuery = true)
    List<CandleVolumeProjection> findVolumeStatsForAllSymbols();

    // ── Query 4: Delivery extremes ────────────────────────────────────────────
    // Finds per symbol+year:
    //   high_deliv_qty  = MAX(delivery_qty)
    //   high_deliv_date = earliest trade_date of that max delivery day
    //   low_deliv_qty   = MIN(delivery_qty)
    //   low_deliv_date  = earliest trade_date of that min delivery day (tie-break)
    @Query(value = """
        SELECT
            agg.symbol                                      AS symbol,
            agg.yr                                          AS year,
            agg.high_deliv_qty                              AS highDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol           = agg.symbol
               AND YEAR(d.trade_date) = agg.yr
               AND d.delivery_qty     = agg.high_deliv_qty) AS highDelivDate,
            agg.low_deliv_qty                               AS lowDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol           = agg.symbol
               AND YEAR(d.trade_date) = agg.yr
               AND d.delivery_qty     = agg.low_deliv_qty)  AS lowDelivDate
        FROM (
            SELECT
                symbol,
                YEAR(trade_date)     AS yr,
                MAX(delivery_qty)    AS high_deliv_qty,
                MIN(delivery_qty)    AS low_deliv_qty
            FROM nse_daily_price
            GROUP BY symbol, YEAR(trade_date)
        ) agg
        ORDER BY agg.symbol ASC, agg.yr ASC
        """, nativeQuery = true)
    List<CandleDeliveryProjection> findDeliveryStatsForAllSymbols();
// =========================================================================
    // CANDLE STATS — 4 focused queries, one method each, timeframe param
    // Supports YEAR / QUARTER / MONTH / WEEK via CASE in period_key expression.
    // All 4 use idx_price_symbol_date (symbol, trade_date) — no full scan.
    // Java merges results by "SYMBOL_periodKey" e.g. "RELIANCE_2024-Q1".
    // =========================================================================

    // ── Query 1: OHLC + period boundaries ────────────────────────────────────
    // period_key per timeframe:
    //   YEAR    → "2024"
    //   QUARTER → "2024-Q1"
    //   MONTH   → "2024-01"
    //   WEEK    → "2024-W03"  (WEEK mode 1 = Monday start, consistent with NSE)
    @Query(value = """
        SELECT
            p.symbol                                            AS symbol,
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(p.trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(p.trade_date), '-Q', QUARTER(p.trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(p.trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(p.trade_date), '-W',
                                    LPAD(WEEK(p.trade_date, 1), 2, '0'))
            END                                                 AS periodKey,
            MIN(p.trade_date)                                   AS candleDate,
            MAX(p.trade_date)                                   AS candleEndDate,
            MAX(p.high_price)                                   AS highPrice,
            MIN(p.low_price)                                    AS lowPrice,
            (SELECT p2.open_price
             FROM nse_daily_price p2
             WHERE p2.symbol     = p.symbol
               AND p2.trade_date = MIN(p.trade_date)
             LIMIT 1)                                           AS openPrice,
            (SELECT COALESCE(p3.last_price, p3.close_price)
             FROM nse_daily_price p3
             WHERE p3.symbol     = p.symbol
               AND p3.trade_date = MAX(p.trade_date)
             LIMIT 1)                                           AS lastPrice
        FROM nse_daily_price p
        GROUP BY
            p.symbol,
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(p.trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(p.trade_date), '-Q', QUARTER(p.trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(p.trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(p.trade_date), '-W',
                                    LPAD(WEEK(p.trade_date, 1), 2, '0'))
            END
        ORDER BY p.symbol ASC, MIN(p.trade_date) ASC
        """, nativeQuery = true)
    List<CandleOhlcProjection> findOhlcStatsForAllSymbols(@Param("timeframe") String timeframe);

    // ── Query 2: High date, Low date, Trend ───────────────────────────────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            agg.period_key                                      AS periodKey,
            (SELECT MIN(h.trade_date)
             FROM nse_daily_price h
             WHERE h.symbol     = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                           LPAD(WEEK(h.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND h.high_price = agg.period_high)             AS highDate,
            (SELECT MIN(l.trade_date)
             FROM nse_daily_price l
             WHERE l.symbol     = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                           LPAD(WEEK(l.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND l.low_price  = agg.period_low)              AS lowDate,
            CASE
                WHEN (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                                    LPAD(WEEK(l.trade_date, 1), 2, '0'))
                            END = agg.period_key
                        AND l.low_price = agg.period_low)
                   < (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                                    LPAD(WEEK(h.trade_date, 1), 2, '0'))
                            END = agg.period_key
                        AND h.high_price = agg.period_high)
                THEN 'UP'
                WHEN (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                                    LPAD(WEEK(h.trade_date, 1), 2, '0'))
                            END = agg.period_key
                        AND h.high_price = agg.period_high)
                   < (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                                    LPAD(WEEK(l.trade_date, 1), 2, '0'))
                            END = agg.period_key
                        AND l.low_price = agg.period_low)
                THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend
        FROM (
            SELECT
                symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END              AS period_key,
                MAX(high_price)  AS period_high,
                MIN(low_price)   AS period_low
            FROM nse_daily_price
            GROUP BY symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END
        ) agg
        ORDER BY agg.symbol ASC, agg.period_key ASC
        """, nativeQuery = true)
    List<CandleHighLowProjection> findHighLowStatsForAllSymbols(@Param("timeframe") String timeframe);

    // ── Query 3: Volume extremes ──────────────────────────────────────────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            agg.period_key                                      AS periodKey,
            agg.high_vol_qty                                    AS highVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol           = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(v.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(v.trade_date), '-Q', QUARTER(v.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(v.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(v.trade_date), '-W',
                                           LPAD(WEEK(v.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND v.traded_quantity  = agg.high_vol_qty)       AS highVolDate,
            agg.low_vol_qty                                     AS lowVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol           = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(v.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(v.trade_date), '-Q', QUARTER(v.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(v.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(v.trade_date), '-W',
                                           LPAD(WEEK(v.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND v.traded_quantity  = agg.low_vol_qty)        AS lowVolDate
        FROM (
            SELECT
                symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END                      AS period_key,
                MAX(traded_quantity)     AS high_vol_qty,
                MIN(traded_quantity)     AS low_vol_qty
            FROM nse_daily_price
            GROUP BY symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END
        ) agg
        ORDER BY agg.symbol ASC, agg.period_key ASC
        """, nativeQuery = true)
    List<CandleVolumeProjection> findVolumeStatsForAllSymbols(@Param("timeframe") String timeframe);

    // ── Query 4: Delivery extremes ────────────────────────────────────────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            agg.period_key                                      AS periodKey,
            agg.high_deliv_qty                                  AS highDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol           = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(d.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(d.trade_date), '-Q', QUARTER(d.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(d.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(d.trade_date), '-W',
                                           LPAD(WEEK(d.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND d.delivery_qty     = agg.high_deliv_qty)    AS highDelivDate,
            agg.low_deliv_qty                                   AS lowDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol           = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(d.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(d.trade_date), '-Q', QUARTER(d.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(d.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(d.trade_date), '-W',
                                           LPAD(WEEK(d.trade_date, 1), 2, '0'))
                   END = agg.period_key
               AND d.delivery_qty     = agg.low_deliv_qty)     AS lowDelivDate
        FROM (
            SELECT
                symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END                  AS period_key,
                MAX(delivery_qty)    AS high_deliv_qty,
                MIN(delivery_qty)    AS low_deliv_qty
            FROM nse_daily_price
            GROUP BY symbol,
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END
        ) agg
        ORDER BY agg.symbol ASC, agg.period_key ASC
        """, nativeQuery = true)
    List<CandleDeliveryProjection> findDeliveryStatsForAllSymbols(@Param("timeframe") String timeframe);

    // =========================================================================
    // CANDLE STATS — 4 focused queries, scoped to a single periodKey
    // Each query accepts :timeframe and :periodKey to process one period at a time.
    // Java calls these in a loop per period — skipping periods already in DB.
    // All queries use idx_price_symbol_date index via GROUP BY symbol + derived key.
    // =========================================================================

    // ── Query 1: OHLC + period boundaries (scoped to one periodKey) ───────────
    @Query(value = """
        SELECT
            p.symbol                                            AS symbol,
            :periodKey                                          AS periodKey,
            MIN(p.trade_date)                                   AS candleDate,
            MAX(p.trade_date)                                   AS candleEndDate,
            MAX(p.high_price)                                   AS highPrice,
            MIN(p.low_price)                                    AS lowPrice,
            (SELECT p2.open_price
             FROM nse_daily_price p2
             WHERE p2.symbol     = p.symbol
               AND p2.trade_date = MIN(p.trade_date)
             LIMIT 1)                                           AS openPrice,
            (SELECT COALESCE(p3.last_price, p3.close_price)
             FROM nse_daily_price p3
             WHERE p3.symbol     = p.symbol
               AND p3.trade_date = MAX(p.trade_date)
             LIMIT 1)                                           AS lastPrice
        FROM nse_daily_price p
        WHERE
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(p.trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(p.trade_date), '-Q', QUARTER(p.trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(p.trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(p.trade_date), '-W',
                                    LPAD(WEEK(p.trade_date, 1), 2, '0'))
            END = :periodKey
        GROUP BY p.symbol
        ORDER BY p.symbol ASC
        """, nativeQuery = true)
    List<CandleOhlcProjection> findOhlcStatsForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Query 2: High date, Low date, Trend (scoped to one periodKey) ─────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            :periodKey                                          AS periodKey,
            (SELECT MIN(h.trade_date)
             FROM nse_daily_price h
             WHERE h.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                           LPAD(WEEK(h.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND h.high_price = agg.period_high)             AS highDate,
            (SELECT MIN(l.trade_date)
             FROM nse_daily_price l
             WHERE l.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                           LPAD(WEEK(l.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND l.low_price = agg.period_low)               AS lowDate,
            CASE
                WHEN (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                                    LPAD(WEEK(l.trade_date, 1), 2, '0'))
                            END = :periodKey
                        AND l.low_price = agg.period_low)
                   < (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                                    LPAD(WEEK(h.trade_date, 1), 2, '0'))
                            END = :periodKey
                        AND h.high_price = agg.period_high)
                THEN 'UP'
                WHEN (SELECT MIN(h.trade_date)
                      FROM nse_daily_price h
                      WHERE h.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(h.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(h.trade_date), '-Q', QUARTER(h.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(h.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(h.trade_date), '-W',
                                                    LPAD(WEEK(h.trade_date, 1), 2, '0'))
                            END = :periodKey
                        AND h.high_price = agg.period_high)
                   < (SELECT MIN(l.trade_date)
                      FROM nse_daily_price l
                      WHERE l.symbol = agg.symbol
                        AND CASE :timeframe
                                WHEN 'YEAR'    THEN CAST(YEAR(l.trade_date) AS CHAR)
                                WHEN 'QUARTER' THEN CONCAT(YEAR(l.trade_date), '-Q', QUARTER(l.trade_date))
                                WHEN 'MONTH'   THEN DATE_FORMAT(l.trade_date, '%Y-%m')
                                WHEN 'WEEK'    THEN CONCAT(YEAR(l.trade_date), '-W',
                                                    LPAD(WEEK(l.trade_date, 1), 2, '0'))
                            END = :periodKey
                        AND l.low_price = agg.period_low)
                THEN 'DOWN'
                ELSE 'SIDE'
            END                                                 AS trend
        FROM (
            SELECT
                symbol,
                MAX(high_price) AS period_high,
                MIN(low_price)  AS period_low
            FROM nse_daily_price
            WHERE
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END = :periodKey
            GROUP BY symbol
        ) agg
        ORDER BY agg.symbol ASC
        """, nativeQuery = true)
    List<CandleHighLowProjection> findHighLowStatsForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Query 3: Volume extremes (scoped to one periodKey) ────────────────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            :periodKey                                          AS periodKey,
            agg.high_vol_qty                                    AS highVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(v.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(v.trade_date), '-Q', QUARTER(v.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(v.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(v.trade_date), '-W',
                                           LPAD(WEEK(v.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND v.traded_quantity = agg.high_vol_qty)        AS highVolDate,
            agg.low_vol_qty                                     AS lowVolQty,
            (SELECT MIN(v.trade_date)
             FROM nse_daily_price v
             WHERE v.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(v.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(v.trade_date), '-Q', QUARTER(v.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(v.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(v.trade_date), '-W',
                                           LPAD(WEEK(v.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND v.traded_quantity = agg.low_vol_qty)         AS lowVolDate
        FROM (
            SELECT
                symbol,
                MAX(traded_quantity) AS high_vol_qty,
                MIN(traded_quantity) AS low_vol_qty
            FROM nse_daily_price
            WHERE
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END = :periodKey
            GROUP BY symbol
        ) agg
        ORDER BY agg.symbol ASC
        """, nativeQuery = true)
    List<CandleVolumeProjection> findVolumeStatsForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Query 4: Delivery extremes (scoped to one periodKey) ──────────────────
    @Query(value = """
        SELECT
            agg.symbol                                          AS symbol,
            :periodKey                                          AS periodKey,
            agg.high_deliv_qty                                  AS highDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(d.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(d.trade_date), '-Q', QUARTER(d.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(d.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(d.trade_date), '-W',
                                           LPAD(WEEK(d.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND d.delivery_qty = agg.high_deliv_qty)        AS highDelivDate,
            agg.low_deliv_qty                                   AS lowDelivQty,
            (SELECT MIN(d.trade_date)
             FROM nse_daily_price d
             WHERE d.symbol = agg.symbol
               AND CASE :timeframe
                       WHEN 'YEAR'    THEN CAST(YEAR(d.trade_date) AS CHAR)
                       WHEN 'QUARTER' THEN CONCAT(YEAR(d.trade_date), '-Q', QUARTER(d.trade_date))
                       WHEN 'MONTH'   THEN DATE_FORMAT(d.trade_date, '%Y-%m')
                       WHEN 'WEEK'    THEN CONCAT(YEAR(d.trade_date), '-W',
                                           LPAD(WEEK(d.trade_date, 1), 2, '0'))
                   END = :periodKey
               AND d.delivery_qty = agg.low_deliv_qty)         AS lowDelivDate
        FROM (
            SELECT
                symbol,
                MAX(delivery_qty) AS high_deliv_qty,
                MIN(delivery_qty) AS low_deliv_qty
            FROM nse_daily_price
            WHERE
                CASE :timeframe
                    WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                    WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                    WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                    WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                        LPAD(WEEK(trade_date, 1), 2, '0'))
                END = :periodKey
            GROUP BY symbol
        ) agg
        ORDER BY agg.symbol ASC
        """, nativeQuery = true)
    List<CandleDeliveryProjection> findDeliveryStatsForPeriod(
            @Param("timeframe") String timeframe,
            @Param("periodKey") String periodKey);

    // ── Distinct period keys that exist in nse_daily_price ────────────────────
    // Used to resolve "all periods" when no from/to is given,
    // and to validate that a requested period actually has data.
    @Query(value = """
        SELECT DISTINCT
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                    LPAD(WEEK(trade_date, 1), 2, '0'))
            END AS period_key
        FROM nse_daily_price
        ORDER BY period_key ASC
        """, nativeQuery = true)
    List<String> findDistinctPeriodKeys1(@Param("timeframe") String timeframe);


    @Query(value = """
        WITH period_data AS (
            -- Step 1: filter to this period only — MySQL uses idx_price_symbol_date
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                last_price,
                close_price,
                traded_quantity,
                delivery_qty
            FROM nse_daily_price
            WHERE trade_date BETWEEN :fromDate AND :toDate
        ),
        enriched AS (
            SELECT
                symbol,
                trade_date,
                open_price,
                high_price,
                low_price,
                last_price,
                close_price,
                traded_quantity,
                delivery_qty,
                MIN(trade_date)       OVER w AS period_start,
                MAX(trade_date)       OVER w AS period_end,
                MAX(high_price)       OVER w AS period_high,
                MIN(low_price)        OVER w AS period_low,
                MAX(traded_quantity)  OVER w AS max_vol,
                MIN(traded_quantity)  OVER w AS min_vol,
                MAX(delivery_qty)     OVER w AS max_deliv,
                MIN(delivery_qty)     OVER w AS min_deliv
            FROM period_data
            WINDOW w AS (PARTITION BY symbol)
        ),
        aggregated AS (
            SELECT
                symbol,
                period_start                                                            AS candle_date,
                period_end                                                              AS candle_end_date,
                MIN(CASE WHEN trade_date = period_start
                 THEN open_price                                          END)  AS open_price,

                MAX(high_price)                                                         AS high_price,
                MIN(low_price)                                                          AS low_price,

                -- Last price: COALESCE(last_price, close_price) of final trading day
                MIN(CASE WHEN trade_date = period_end
                         THEN COALESCE(last_price, close_price)                   END)  AS last_price,

                -- Date when period high was first reached (tie-break: earliest)
                MIN(CASE WHEN high_price = period_high THEN trade_date            END)  AS high_price_date,

                -- Date when period low was first reached (tie-break: earliest)
                MIN(CASE WHEN low_price  = period_low  THEN trade_date            END)  AS low_price_date,

                -- Highest volume day
                MAX(traded_quantity)                                                     AS high_vol_qty,
                MIN(CASE WHEN traded_quantity = max_vol   THEN trade_date         END)  AS high_vol_date,

                -- Lowest volume day (tie-break: earliest date)
                MIN(traded_quantity)                                                     AS low_vol_qty,
                MIN(CASE WHEN traded_quantity = min_vol   THEN trade_date         END)  AS low_vol_date,

                -- Highest delivery day
                MAX(delivery_qty)                                                        AS high_deliv_qty,
                MIN(CASE WHEN delivery_qty    = max_deliv THEN trade_date         END)  AS high_deliv_date,

                -- Lowest delivery day (tie-break: earliest date)
                MIN(delivery_qty)                                                        AS low_deliv_qty,
                MIN(CASE WHEN delivery_qty    = min_deliv THEN trade_date         END)  AS low_deliv_date

            FROM enriched
            GROUP BY symbol, period_start, period_end
        )
        -- Step 4: final output — trend derived from high_price_date vs low_price_date
        SELECT
            symbol                                                  AS symbol,
            candle_date                                             AS candleDate,
            candle_end_date                                         AS candleEndDate,
            open_price                                              AS openPrice,
            high_price                                              AS highPrice,
            low_price                                               AS lowPrice,
            last_price                                              AS lastPrice,
            high_price_date                                         AS highPriceDate,
            low_price_date                                          AS lowPriceDate,
            CASE
                WHEN low_price_date  < high_price_date THEN 'UP'
                WHEN high_price_date < low_price_date  THEN 'DOWN'
                ELSE 'SIDE'
            END                                                     AS trend,
            high_vol_qty                                            AS highVolQty,
            high_vol_date                                           AS highVolDate,
            low_vol_qty                                             AS lowVolQty,
            low_vol_date                                            AS lowVolDate,
            high_deliv_qty                                          AS highDelivQty,
            high_deliv_date                                         AS highDelivDate,
            low_deliv_qty                                           AS lowDelivQty,
            low_deliv_date                                          AS lowDelivDate
        FROM aggregated
        ORDER BY symbol ASC
        """, nativeQuery = true)
    List<CandleStatsProjection> findStatsForPeriod(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate")   LocalDate toDate);

    // ── Distinct period keys that exist in nse_daily_price ────────────────────
    // Used by service to resolve "all periods" when no from/to is given.
    // Uses DATE_FORMAT — acceptable here since it runs once per build call,
    // not per row, and the result is just a small list of period keys.
    @Query(value = """
        SELECT DISTINCT
            CASE :timeframe
                WHEN 'YEAR'    THEN CAST(YEAR(trade_date) AS CHAR)
                WHEN 'QUARTER' THEN CONCAT(YEAR(trade_date), '-Q', QUARTER(trade_date))
                WHEN 'MONTH'   THEN DATE_FORMAT(trade_date, '%Y-%m')
                WHEN 'WEEK'    THEN CONCAT(YEAR(trade_date), '-W',
                                    LPAD(WEEK(trade_date, 1), 2, '0'))
            END AS period_key
        FROM nse_daily_price
        ORDER BY period_key ASC
        """, nativeQuery = true)
    List<String> findDistinctPeriodKeys(@Param("timeframe") String timeframe);
}
