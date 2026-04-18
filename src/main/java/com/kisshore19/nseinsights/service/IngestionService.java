package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.request.DownloadRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.entity.DownloadLog;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.exception.DateNotFoundException;
import com.kisshore19.nseinsights.exception.InvalidDateException;
import com.kisshore19.nseinsights.repository.DownloadLogRepository;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IngestionService {

    private final NseDailyPriceRepository priceRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final NseBhavatopyDownloader bhavatopyDownloader;
    private final CsvParserService csvParser;
    private final ExecutorService downloadExecutor;

    @Value("${nse.download.thread-pool-size:5}")
    private int threadPoolSize;

    public IngestionService(
            NseDailyPriceRepository priceRepository,
            DownloadLogRepository downloadLogRepository,
            NseBhavatopyDownloader bhavatopyDownloader,
            CsvParserService csvParser,
            @Qualifier("nseDownloadExecutor") ExecutorService downloadExecutor) {
        this.priceRepository = priceRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.bhavatopyDownloader = bhavatopyDownloader;
        this.csvParser = csvParser;
        this.downloadExecutor = downloadExecutor;
    }

    // ── Range Download with Parallel Threads ──────────────────────────────────
    public RangeDownloadResponse downloadRange(DownloadRequest request) {
        LocalDate fromDate = request.getFromTradeDate();
        LocalDate toDate   = request.getEffectiveToDate();

        if (toDate.isBefore(fromDate)) {
            throw new InvalidDateException(
                    "toTradeDate (" + toDate + ") cannot be before fromTradeDate (" + fromDate + ")");
        }

        // Build list of all dates in range
        List<LocalDate> allDates = fromDate.datesUntil(toDate.plusDays(1))
                .collect(Collectors.toList());

        log.info("Starting parallel download: {} to {} ({} dates) using {} threads",
                fromDate, toDate, allDates.size(), threadPoolSize);

        long overallStart = System.currentTimeMillis();

        // Submit all dates as parallel tasks
        List<Future<DateDownloadResult>> futures = new ArrayList<>();
        for (LocalDate date : allDates) {
            final LocalDate taskDate = date;
            Future<DateDownloadResult> future = downloadExecutor.submit(
                    () -> downloadSingleDate(taskDate, request.isOverwrite())
            );
            futures.add(future);
        }

        // Collect results in order
        List<DateDownloadResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            LocalDate date = allDates.get(i);
            try {
                DateDownloadResult result = futures.get(i).get(60, TimeUnit.SECONDS);
                results.add(result);
            } catch (TimeoutException ex) {
                log.warn("✗ {} — TIMEOUT after 60 seconds", date);
                results.add(DateDownloadResult.builder()
                        .tradeDate(date)
                        .status("FAILED")
                        .message("Timeout after 60 seconds")
                        .build());
            } catch (Exception ex) {
                log.warn("✗ {} — FAILED: {}", date, ex.getMessage());
                results.add(DateDownloadResult.builder()
                        .tradeDate(date)
                        .status("FAILED")
                        .message(ex.getMessage())
                        .build());
            }
        }

        // Sort results by date ascending
        results.sort(Comparator.comparing(DateDownloadResult::getTradeDate));

        // Tally counts
        int successCount = 0, failedCount = 0, skippedCount = 0, totalRecords = 0;
        for (DateDownloadResult r : results) {
            switch (r.getStatus()) {
                case "SUCCESS"        -> { successCount++; totalRecords += r.getRecordsLoaded() != null ? r.getRecordsLoaded() : 0; }
                case "FAILED"         -> failedCount++;
                case "ALREADY_EXISTS" -> skippedCount++;
            }
        }

        long totalTime = System.currentTimeMillis() - overallStart;

        log.info("Parallel download complete: {} to {} | Success={}, Failed={}, Skipped={}, Records={}, Time={}ms",
                fromDate, toDate, successCount, failedCount, skippedCount, totalRecords, totalTime);

        return RangeDownloadResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalDatesRequested(allDates.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .totalRecordsLoaded(totalRecords)
                .totalTimeTakenMs(totalTime)
                .results(results)
                .build();
    }

    // ── Download Single Date ───────────────────────────────────────────────────
    @Transactional
    public DateDownloadResult downloadSingleDate(LocalDate tradeDate, boolean overwrite) {
        long startTime = System.currentTimeMillis();
        log.info("Processing date: {}", tradeDate);

        // 1. Check if already exists
        boolean exists = downloadLogRepository
                .existsByTradeDateAndStatus(tradeDate, DownloadLog.STATUS_SUCCESS);

        if (exists && !overwrite) {
            log.info("Skipping {} — already downloaded.", tradeDate);
            return DateDownloadResult.builder()
                    .tradeDate(tradeDate)
                    .status("ALREADY_EXISTS")
                    .message("Already downloaded. Pass overwrite=true to re-download.")
                    .build();
        }

        // 2. If overwrite, delete existing
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
                throw new RuntimeException("No EQ records found for " + tradeDate);
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
            long timeTaken = System.currentTimeMillis() - startTime;
            downloadLog.setStatus(DownloadLog.STATUS_FAILED);
            downloadLog.setErrorMessage(ex.getMessage());
            downloadLog.setCompletedAt(LocalDateTime.now());
            downloadLogRepository.save(downloadLog);
            log.warn("✗ {} — FAILED: {}", tradeDate, ex.getMessage());

            return DateDownloadResult.builder()
                    .tradeDate(tradeDate)
                    .status("FAILED")
                    .message(ex.getMessage())
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
                .latestDate(dates.isEmpty() ? null : dates.get(0))
                .oldestDate(dates.isEmpty() ? null : dates.get(dates.size() - 1))
                .dates(dates)
                .totalDays(dates.size())
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