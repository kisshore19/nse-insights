package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.request.DownloadRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.entity.DownloadLog;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.exception.*;
import com.kisshore19.nseinsights.repository.DownloadLogRepository;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final NseDailyPriceRepository priceRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final NseBhavatopyDownloader bhavatopyDownloader;
    private final MtoFileDownloader mtoDownloader;
    private final CsvParserService csvParser;
    private final EntityManager entityManager;

    // ── Get Available Dates ────────────────────────────────────────────────────
    public AvailableDatesResponse getAvailableDates() {
        List<LocalDate> dates = priceRepository.findAllDistinctTradeDates();
        LocalDate latest = dates.isEmpty() ? null : dates.get(0);
        LocalDate oldest = dates.isEmpty() ? null : dates.get(dates.size() - 1);

        return AvailableDatesResponse.builder()
                .dates(dates)
                .totalDates(dates.size())
                .latestDate(latest)
                .oldestDate(oldest)
                .build();
    }

    // ── Download & Store ───────────────────────────────────────────────────────
    @Transactional
    public DownloadResponse downloadAndStore(DownloadRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("Starting download for trade date: {}", tradeDate);

        // 1. Check if data already exists
        boolean exists = downloadLogRepository
                .existsByTradeDateAndStatus(tradeDate, DownloadLog.STATUS_SUCCESS);

        if (exists && !request.isOverwrite()) {
            long count = priceRepository.countByTradeDate(tradeDate);
            throw new DataAlreadyExistsException(tradeDate, (int) count);
        }

        // 2. If overwrite, delete existing data first
        if (exists && request.isOverwrite()) {
            log.info("Overwrite=true, deleting existing data for {}", tradeDate);
            priceRepository.deleteByTradeDate(tradeDate);
        }

        // 3. Create initial download log entry
        DownloadLog downloadLog = DownloadLog.builder()
                .tradeDate(tradeDate)
                .status(DownloadLog.STATUS_PARTIAL)
                .downloadedAt(LocalDateTime.now())
                .build();
        downloadLog = downloadLogRepository.save(downloadLog);

        long startTime = System.currentTimeMillis();

        try {
            // 4. Download Bhavacopy only (MTO not required - delivery data in Bhavacopy)
            log.info("Downloading Bhavacopy for {}", tradeDate);
            NseBhavatopyDownloader.BhavatopyResult bhavResult =
                    bhavatopyDownloader.download(tradeDate);
            downloadLog.setBhavatopyUrl(bhavResult.getUrl());
            downloadLog.setFileName(bhavResult.getFileName());

            // 5. Parse Bhavacopy CSV (includes delivery data - MTO not required)
            log.info("Parsing Bhavacopy data");
            List<NseDailyPrice> records = csvParser.parseAndMerge(
                    bhavResult.getCsvContent(),
                    null,  // No MTO file needed - delivery data in CSV
                    tradeDate
            );

            // 7. Bulk save to DB
            log.info("Saving {} records to database for {}", records.size(), tradeDate);

            if (records.isEmpty()) {
                log.warn("No records to save for {}", tradeDate);
                throw new RuntimeException("CSV parsing returned 0 records. Please verify NSE data format.");
            }

            try {
                priceRepository.saveAll(records);
                entityManager.flush();  // Ensure all records are persisted immediately
                log.info("Successfully saved and flushed all {} records", records.size());
            } catch (Exception ex) {
                log.error("Failed to save records: {}", ex.getMessage(), ex);
                throw new RuntimeException("Failed to save records to database: " + ex.getMessage(), ex);
            }

            // 8. Update download log — SUCCESS
            long timeTaken = System.currentTimeMillis() - startTime;
            downloadLog.setStatus(DownloadLog.STATUS_SUCCESS);
            downloadLog.setRecordCount(records.size());
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);

            log.info("Download complete for {} — {} records in {}ms",
                    tradeDate, records.size(), timeTaken);

            return DownloadResponse.builder()
                    .tradeDate(tradeDate)
                    .recordsLoaded(records.size())
                    .bhavatopyFile(bhavResult.getFileName())
                    .timeTakenMs(timeTaken)
                    .downloadLogId(downloadLog.getId())
                    .build();

        } catch (NseUnavailableException ex) {
            // Update log with failure
            downloadLog.setStatus(DownloadLog.STATUS_FAILED);
            downloadLog.setErrorMessage(ex.getMessage());
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);
            throw ex;
        } catch (Exception ex) {
            downloadLog.setStatus(DownloadLog.STATUS_FAILED);
            downloadLog.setErrorMessage("Unexpected error: " + ex.getMessage());
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);
            log.error("Download failed for {}: {}", tradeDate, ex.getMessage(), ex);
            throw new RuntimeException("Download failed for " + tradeDate, ex);
        }
    }

    // ── Get Download Status ────────────────────────────────────────────────────
    public DownloadStatusResponse getStatus(LocalDate tradeDate) {
        return downloadLogRepository
                .findTopByTradeDateAndStatusOrderByDownloadedAtDesc(
                        tradeDate, DownloadLog.STATUS_SUCCESS)
                .map(log -> DownloadStatusResponse.builder()
                        .tradeDate(tradeDate)
                        .downloaded(true)
                        .recordCount(log.getRecordCount())
                        .downloadedAt(log.getDownloadedAt())
                        .build())
                .orElse(DownloadStatusResponse.builder()
                        .tradeDate(tradeDate)
                        .downloaded(false)
                        .build());
    }

    // ── Get Download History ───────────────────────────────────────────────────
    public DownloadHistoryResponse getHistory(int page, int size, String status) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "downloadedAt"));

        Page<DownloadLog> logPage = status.equalsIgnoreCase("ALL")
                ? downloadLogRepository.findAllByOrderByDownloadedAtDesc(pageRequest)
                : downloadLogRepository.findByStatusOrderByDownloadedAtDesc(status, pageRequest);

        List<DownloadHistoryItem> items = logPage.getContent().stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());

        return DownloadHistoryResponse.builder()
                .content(items)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();
    }

    // ── Delete Data for a Date ─────────────────────────────────────────────────
    @Transactional
    public void deleteByDate(LocalDate tradeDate) {
        if (!priceRepository.existsByTradeDate(tradeDate)) {
            throw new DateNotFoundException(
                    "No data found for " + tradeDate + ". Nothing to delete.");
        }
        int deleted = priceRepository.deleteByTradeDate(tradeDate);
        log.info("Deleted {} records for {}", deleted, tradeDate);

        // Add audit log entry
        DownloadLog auditLog = DownloadLog.builder()
                .tradeDate(tradeDate)
                .status(DownloadLog.STATUS_DELETED)
                .recordCount(deleted)
                .downloadedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        downloadLogRepository.save(auditLog);
    }

    // ── Ingestion Summary ──────────────────────────────────────────────────────
    public IngestionSummaryResponse getSummary() {
        return IngestionSummaryResponse.builder()
                .totalDatesLoaded(priceRepository.countDistinctTradeDates())
                .latestTradeDate(priceRepository.findLatestTradeDate().orElse(null))
                .oldestTradeDate(priceRepository.findOldestTradeDate().orElse(null))
                .totalRecords(priceRepository.count())
                .lastDownloadedAt(downloadLogRepository.findTopByOrderByDownloadedAtDesc()
                        .map(DownloadLog::getDownloadedAt).orElse(null))
                .failedDownloads(downloadLogRepository.countByStatus(DownloadLog.STATUS_FAILED))
                .build();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private DownloadHistoryItem toHistoryItem(DownloadLog log) {
        Long timeTaken = (log.getCompletedAt() != null && log.getDownloadedAt() != null)
                ? java.time.Duration.between(log.getDownloadedAt(), log.getCompletedAt()).toMillis()
                : null;

        return DownloadHistoryItem.builder()
                .id(log.getId())
                .tradeDate(log.getTradeDate())
                .status(log.getStatus())
                .recordCount(log.getRecordCount())
                .fileName(log.getFileName())
                .errorMessage(log.getErrorMessage())
                .downloadedAt(log.getDownloadedAt())
                .completedAt(log.getCompletedAt())
                .timeTakenMs(timeTaken)
                .build();
    }
}
