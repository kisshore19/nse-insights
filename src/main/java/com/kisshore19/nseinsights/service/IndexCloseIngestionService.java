package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.request.DownloadRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.entity.IndexDailyClose;
import com.kisshore19.nseinsights.exception.DateNotFoundException;
import com.kisshore19.nseinsights.exception.InvalidDateException;
import com.kisshore19.nseinsights.repository.IndexDailyCloseRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndexCloseIngestionService {

    private final IndexDailyCloseRepository closeRepository;
    private final NseIndexCloseDownloader   closeDownloader;
    private final IndexCloseCsvParser       csvParser;
    private final ExecutorService           downloadExecutor;

    @Value("${nse.download.thread-pool-size:5}")
    private int threadPoolSize;

    public IndexCloseIngestionService(
            IndexDailyCloseRepository closeRepository,
            NseIndexCloseDownloader closeDownloader,
            IndexCloseCsvParser csvParser,
            @Qualifier("nseDownloadExecutor") ExecutorService downloadExecutor) {
        this.closeRepository  = closeRepository;
        this.closeDownloader  = closeDownloader;
        this.csvParser        = csvParser;
        this.downloadExecutor = downloadExecutor;
    }

    // ── Range Download (parallel) ─────────────────────────────────────────────
    public RangeDownloadResponse downloadRange(DownloadRequest request) {
        LocalDate fromDate = request.getFromTradeDate();
        LocalDate toDate   = request.getEffectiveToDate();

        if (toDate.isBefore(fromDate)) {
            throw new InvalidDateException(
                    "toTradeDate (" + toDate + ") cannot be before fromTradeDate (" + fromDate + ")");
        }

        List<LocalDate> allDates = fromDate.datesUntil(toDate.plusDays(1))
                .collect(Collectors.toList());

        log.info("Starting index close download: {} to {} ({} dates) with {} threads",
                fromDate, toDate, allDates.size(), threadPoolSize);

        long overallStart = System.currentTimeMillis();

        List<Future<DateDownloadResult>> futures = new ArrayList<>();
        for (LocalDate date : allDates) {
            futures.add(downloadExecutor.submit(
                    () -> downloadSingleDate(date, request.isOverwrite())));
        }

        List<DateDownloadResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            LocalDate date = allDates.get(i);
            try {
                results.add(futures.get(i).get(60, TimeUnit.SECONDS));
            } catch (TimeoutException ex) {
                log.warn("✗ {} — TIMEOUT", date);
                results.add(DateDownloadResult.builder()
                        .tradeDate(date).status("FAILED")
                        .message("Timeout after 60 seconds").build());
            } catch (Exception ex) {
                log.warn("✗ {} — {}", date, ex.getMessage());
                results.add(DateDownloadResult.builder()
                        .tradeDate(date).status("FAILED")
                        .message(ex.getMessage()).build());
            }
        }

        results.sort(Comparator.comparing(DateDownloadResult::getTradeDate));

        int successCount = 0, failedCount = 0, skippedCount = 0, totalRecords = 0;
        for (DateDownloadResult r : results) {
            switch (r.getStatus()) {
                case "SUCCESS"        -> { successCount++; totalRecords += r.getRecordsLoaded() != null ? r.getRecordsLoaded() : 0; }
                case "FAILED"         -> failedCount++;
                case "ALREADY_EXISTS" -> skippedCount++;
            }
        }

        long totalTime = System.currentTimeMillis() - overallStart;
        log.info("Index close download done: Success={}, Failed={}, Skipped={}, Records={}, {}ms",
                successCount, failedCount, skippedCount, totalRecords, totalTime);

        return RangeDownloadResponse.builder()
                .fromDate(fromDate).toDate(toDate)
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
        log.info("Processing index close for: {}", tradeDate);

        boolean exists = closeRepository.existsByTradeDate(tradeDate);

        if (exists && !overwrite) {
            log.info("Skipping {} — already downloaded.", tradeDate);
            return DateDownloadResult.builder()
                    .tradeDate(tradeDate).status("ALREADY_EXISTS")
                    .message("Already downloaded. Pass overwrite=true to re-download.")
                    .build();
        }

        if (exists) {
            log.info("Overwrite=true, deleting existing data for {}", tradeDate);
            closeRepository.deleteByTradeDate(tradeDate);
        }

        try {
            NseIndexCloseDownloader.IndexCloseResult downloadResult =
                    closeDownloader.download(tradeDate);

            List<IndexDailyClose> records =
                    csvParser.parse(downloadResult.getCsvContent(), tradeDate);

            if (records.isEmpty()) {
                throw new RuntimeException("No records found for " + tradeDate);
            }

            closeRepository.saveAll(records);

            long timeTaken = System.currentTimeMillis() - startTime;
            log.info("✓ {} — {} indices in {}ms", tradeDate, records.size(), timeTaken);

            return DateDownloadResult.builder()
                    .tradeDate(tradeDate).status("SUCCESS")
                    .recordsLoaded(records.size())
                    .timeTakenMs(timeTaken)
                    .build();

        } catch (Exception ex) {
            long timeTaken = System.currentTimeMillis() - startTime;
            log.warn("✗ {} — FAILED: {}", tradeDate, ex.getMessage());
            return DateDownloadResult.builder()
                    .tradeDate(tradeDate).status("FAILED")
                    .message(ex.getMessage()).timeTakenMs(timeTaken)
                    .build();
        }
    }

    // ── Available Dates ───────────────────────────────────────────────────────
    public AvailableDatesResponse getAvailableDates() {
        List<LocalDate> dates = closeRepository.findAllDistinctTradeDates();
        return AvailableDatesResponse.builder()
                .latestDate(dates.isEmpty() ? null : dates.get(0))
                .oldestDate(dates.isEmpty() ? null : dates.get(dates.size() - 1))
                .dates(dates)
                .totalDays(dates.size())
                .build();
    }

    // ── Status for a Date ─────────────────────────────────────────────────────
    public DownloadStatusResponse getStatus(LocalDate tradeDate) {
        boolean exists = closeRepository.existsByTradeDate(tradeDate);
        if (!exists) {
            return DownloadStatusResponse.builder()
                    .tradeDate(tradeDate).downloaded(false).build();
        }
        long count = closeRepository.findByTradeDateOrderByIndexNameAsc(tradeDate).size();
        return DownloadStatusResponse.builder()
                .tradeDate(tradeDate).downloaded(true)
                .recordCount((int) count)
                .build();
    }

    // ── Delete Data for a Date ────────────────────────────────────────────────
    @Transactional
    public void deleteByDate(LocalDate tradeDate) {
        if (!closeRepository.existsByTradeDate(tradeDate)) {
            throw new DateNotFoundException(
                    "No index close data found for " + tradeDate + ". Nothing to delete.");
        }
        int deleted = closeRepository.deleteByTradeDate(tradeDate);
        log.info("Deleted {} index close records for {}", deleted, tradeDate);
    }

    // ── Summary ───────────────────────────────────────────────────────────────
    public IngestionSummaryResponse getSummary() {
        return IngestionSummaryResponse.builder()
                .totalDatesLoaded(closeRepository.countDistinctTradeDates())
                .latestTradeDate(closeRepository.findLatestTradeDate().orElse(null))
                .oldestTradeDate(closeRepository.findOldestTradeDate().orElse(null))
                .totalRecords(closeRepository.count())
                .build();
    }
}
