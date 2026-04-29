package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.IndexDailyClose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndexDailyCloseRepository extends JpaRepository<IndexDailyClose, Long> {

    boolean existsByTradeDate(LocalDate tradeDate);

    @Modifying
    @Query("DELETE FROM IndexDailyClose c WHERE c.tradeDate = :tradeDate")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Query("SELECT DISTINCT c.tradeDate FROM IndexDailyClose c ORDER BY c.tradeDate DESC")
    List<LocalDate> findAllDistinctTradeDates();

    @Query("SELECT MAX(c.tradeDate) FROM IndexDailyClose c")
    Optional<LocalDate> findLatestTradeDate();

    @Query("SELECT MIN(c.tradeDate) FROM IndexDailyClose c")
    Optional<LocalDate> findOldestTradeDate();

    @Query("SELECT COUNT(DISTINCT c.tradeDate) FROM IndexDailyClose c")
    long countDistinctTradeDates();

    // All index values for a specific date, ordered by index name
    List<IndexDailyClose> findByTradeDateOrderByIndexNameAsc(LocalDate tradeDate);

    // Historical data for a specific index
    List<IndexDailyClose> findByIndexNameOrderByTradeDateAsc(String indexName);

    // Specific index on a specific date
    Optional<IndexDailyClose> findByIndexNameAndTradeDate(String indexName, LocalDate tradeDate);
}
