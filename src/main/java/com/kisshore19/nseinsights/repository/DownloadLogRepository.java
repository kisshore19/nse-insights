package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.DownloadLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Integer> {

    // Check if a successful download exists for a date
    boolean existsByTradeDateAndStatus(LocalDate tradeDate, String status);

    // Get the latest successful download log for a date
    Optional<DownloadLog> findTopByTradeDateAndStatusOrderByDownloadedAtDesc(
            LocalDate tradeDate, String status);

    // Paginated history — all statuses
    Page<DownloadLog> findAllByOrderByDownloadedAtDesc(Pageable pageable);

    // Paginated history — filtered by status
    Page<DownloadLog> findByStatusOrderByDownloadedAtDesc(String status, Pageable pageable);

    // Count failed downloads
    long countByStatus(String status);

    // Get latest download log entry
    Optional<DownloadLog> findTopByOrderByDownloadedAtDesc();
}
