package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.NseDailyPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NseDailyPriceRepository extends JpaRepository<NseDailyPrice, Long> {

    // ── MODULE 1: Ingestion ─────────────────────────────────────────────────
    // Find all records for a specific trading date
    List<NseDailyPrice> findByTradeDateOrderByPctChangeDesc(LocalDate tradeDate);

    // Find a specific stock on a specific date
    Optional<NseDailyPrice> findByTradeDateAndSymbol(LocalDate tradeDate, String symbol);

    // Count records for a date (used for duplicate check)
    long countByTradeDate(LocalDate tradeDate);

    // Delete all records for a date
    @Modifying
    @Query("DELETE FROM NseDailyPrice n WHERE n.tradeDate = :tradeDate")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    // Get latest available trade date
    @Query("SELECT MAX(n.tradeDate) FROM NseDailyPrice n")
    Optional<LocalDate> findLatestTradeDate();

    // Get oldest available trade date
    @Query("SELECT MIN(n.tradeDate) FROM NseDailyPrice n")
    Optional<LocalDate> findOldestTradeDate();

    // Count total distinct trading dates loaded
    @Query("SELECT COUNT(DISTINCT n.tradeDate) FROM NseDailyPrice n")
    long countDistinctTradeDates();

    // Check if data exists for a date
    boolean existsByTradeDate(LocalDate tradeDate);

    // ── MODULE 2: Data Explorer ────────────────────────────────────────────────

    // Get all distinct trading dates available
    @Query("SELECT DISTINCT n.tradeDate FROM NseDailyPrice n ORDER BY n.tradeDate DESC")
    List<LocalDate> findAllDistinctTradeDates();

    // Advanced search with multiple filters
    @Query("""
        SELECT n FROM NseDailyPrice n
        WHERE (:tradeDate IS NULL OR n.tradeDate = :tradeDate)
        AND (:symbol IS NULL OR n.symbol LIKE CONCAT('%', :symbol, '%'))
        AND (:minPrice IS NULL OR n.closePrice >= :minPrice)
        AND (:maxPrice IS NULL OR n.closePrice <= :maxPrice)
        AND (:minVolume IS NULL OR n.tradedQuantity >= :minVolume)
        AND (:minPctChange IS NULL OR n.pctChange >= :minPctChange)
        AND (:maxPctChange IS NULL OR n.pctChange <= :maxPctChange)
        AND (:minDeliveryPct IS NULL OR n.deliveryPct >= :minDeliveryPct)
    """)
    Page<NseDailyPrice> searchStocks(
            @Param("tradeDate") LocalDate tradeDate,
            @Param("symbol") String symbol,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minVolume") Long minVolume,
            @Param("minPctChange") BigDecimal minPctChange,
            @Param("maxPctChange") BigDecimal maxPctChange,
            @Param("minDeliveryPct") BigDecimal minDeliveryPct,
            Pageable pageable
    );

    // Get all distinct sectors from index
    @Query("""
        SELECT DISTINCT im.sector FROM IndexMaster im
        WHERE im.isActive = true
        ORDER BY im.sector ASC
    """)
    List<String> findAllDistinctSectors();
    @Query("""
        SELECT n FROM NseDailyPrice n
        WHERE n.tradeDate = :tradeDate
        AND n.pctChange IS NOT NULL
        ORDER BY n.pctChange DESC
    """)
    List<NseDailyPrice> findTopGainers(
            @Param("tradeDate") LocalDate tradeDate,
            Pageable pageable
    );

    // Get top losers for a date
    @Query("""
        SELECT n FROM NseDailyPrice n
        WHERE n.tradeDate = :tradeDate
        AND n.pctChange IS NOT NULL
        ORDER BY n.pctChange ASC
    """)
    List<NseDailyPrice> findTopLosers(
            @Param("tradeDate") LocalDate tradeDate,
            Pageable pageable
    );
}
