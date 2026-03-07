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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final NseDailyPriceRepository priceRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final NseBhavatopyDownloader bhavatopyDownloader;
    private final CsvParserService csvParser;

    // ── Range Download (fromDate to toDate) ───────────────────────────────────
    public RangeDownloadResponse downloadRange(DownloadRequest request) {
        LocalDate fromDate  = request.getFromTradeDate();
        LocalDate toDate    = request.getEffectiveToDate();

        // Validate date range
        if (toDate.isBefore(fromDate)) {
            throw new InvalidDateException(
                    "toTradeDate (" + toDate + ") cannot be before fromTradeDate (" + fromDate + ")");
        }

        long overallStart = System.currentTimeMillis();
        List<DateDownloadResult> results = new ArrayList<>();

        int successCount  = 0;
        int failedCount   = 0;
        int skippedCount  = 0;
        int totalRecords  = 0;

        // Loop through each date in range
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            DateDownloadResult result = downloadSingleDate(current, request.isOverwrite());
            results.add(result);

            switch (result.getStatus()) {
                case "SUCCESS"        -> { successCount++; totalRecords += result.getRecordsLoaded(); }
                case "FAILED"         -> failedCount++;
                case "ALREADY_EXISTS" -> skippedCount++;
            }

            current = current.plusDays(1);
        }

        long totalTime = System.currentTimeMillis() - overallStart;

        log.info("Range download complete: {} to {} | Success={}, Failed={}, Skipped={}, Records={}",
                fromDate, toDate, successCount, failedCount, skippedCount, totalRecords);

        return RangeDownloadResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalDatesRequested((int) fromDate.datesUntil(toDate.plusDays(1)).count())
                .successCount(successCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .totalRecordsLoaded(totalRecords)
                .totalTimeTakenMs(totalTime)
                .results(results)
                .build();
    }

    // ── Download Single Date (used internally by range loop) ──────────────────
    @Transactional
    public DateDownloadResult downloadSingleDate(LocalDate tradeDate, boolean overwrite) {
        long startTime = System.currentTimeMillis();
        log.info("Processing date: {}", tradeDate);

        // 1. Check if already exists
        boolean exists = downloadLogRepository
                .existsByTradeDateAndStatus(tradeDate, DownloadLog.STATUS_SUCCESS);

        if (exists && !overwrite) {
            log.info("Skipping {} — already downloaded. Use overwrite=true to re-download.", tradeDate);
            return DateDownloadResult.builder()
                    .tradeDate(tradeDate)
                    .status("ALREADY_EXISTS")
                    .message("Already downloaded. Pass overwrite=true to re-download.")
                    .build();
        }

        // 2. If overwrite, delete existing data
        if (exists && overwrite) {
            log.info("Overwrite=true, deleting existing data for {}", tradeDate);
            priceRepository.deleteByTradeDate(tradeDate);
        }

        // 3. Create download log
        DownloadLog downloadLog = DownloadLog.builder()
                .tradeDate(tradeDate)
                .status(DownloadLog.STATUS_PARTIAL)
                .downloadedAt(LocalDateTime.now())
                .build();
        downloadLog = downloadLogRepository.save(downloadLog);

        try {
            // 4. Download Bhavacopy
            NseBhavatopyDownloader.BhavatopyResult bhavResult =
                    bhavatopyDownloader.download(tradeDate);
            downloadLog.setBhavatopyUrl(bhavResult.getUrl());
            downloadLog.setFileName(bhavResult.getFileName());

            // 5. Parse CSV
            List<NseDailyPrice> records = csvParser.parse(bhavResult.getCsvContent(), tradeDate);

            if (records.isEmpty()) {
                throw new RuntimeException("No EQ records found in file for " + tradeDate);
            }

            // 6. Bulk save
            priceRepository.saveAll(records);

            // 7. Update log — SUCCESS
            long timeTaken = System.currentTimeMillis() - startTime;
            downloadLog.setStatus(DownloadLog.STATUS_SUCCESS);
            downloadLog.setRecordCount(records.size());
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);

            log.info("✓ {} — {} records in {}ms", tradeDate, records.size(), timeTaken);

            return DateDownloadResult.builder()
                    .tradeDate(tradeDate)
                    .status("SUCCESS")
                    .recordsLoaded(records.size())
                    .timeTakenMs(timeTaken)
                    .build();

        } catch (Exception ex) {
            // Log failure but DO NOT rethrow — let range loop continue
            long timeTaken = System.currentTimeMillis() - startTime;
            String errorMsg = ex.getMessage();

            downloadLog.setStatus(DownloadLog.STATUS_FAILED);
            downloadLog.setErrorMessage(errorMsg);
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);

            log.warn("✗ {} — FAILED: {}", tradeDate, errorMsg);

            return DateDownloadResult.builder()
                    .tradeDate(tradeDate)
                    .status("FAILED")
                    .message(errorMsg)
                    .timeTakenMs(timeTaken)
                    .build();
        }
    }

    // ── Get Download Status ────────────────────────────────────────────────────
    public DownloadStatusResponse getStatus(LocalDate tradeDate) {
        return downloadLogRepository
                .findTopByTradeDateAndStatusOrderByDownloadedAtDesc(
                        tradeDate, DownloadLog.STATUS_SUCCESS)
                .map(dl -> DownloadStatusResponse.builder()
                        .tradeDate(tradeDate)
                        .downloaded(true)
                        .recordCount(dl.getRecordCount())
                        .downloadedAt(dl.getDownloadedAt())
                        .build())
                .orElse(DownloadStatusResponse.builder()
                        .tradeDate(tradeDate)
                        .downloaded(false)
                        .build());
    }

    // ── Get Available Dates ────────────────────────────────────────────────────
    public AvailableDatesResponse getAvailableDates() {
        List<LocalDate> dates = priceRepository.findAllDistinctTradeDates();
        return AvailableDatesResponse.builder()
                .dates(dates)
                .totalDays(dates.size())
                .latestDate(dates.isEmpty() ? null : dates.get(0))
                .oldestDate(dates.isEmpty() ? null : dates.get(dates.size() - 1))
                .build();
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
    private DownloadHistoryItem toHistoryItem(DownloadLog dl) {
        Long timeTaken = (dl.getCompletedAt() != null && dl.getDownloadedAt() != null)
                ? java.time.Duration.between(dl.getDownloadedAt(), dl.getCompletedAt()).toMillis()
                : null;

        return DownloadHistoryItem.builder()
                .id(dl.getId())
                .tradeDate(dl.getTradeDate())
                .status(dl.getStatus())
                .recordCount(dl.getRecordCount())
                .fileName(dl.getFileName())
                .errorMessage(dl.getErrorMessage())
                .downloadedAt(dl.getDownloadedAt())
                .completedAt(dl.getCompletedAt())
                .timeTakenMs(timeTaken)
                .build();
    }
}