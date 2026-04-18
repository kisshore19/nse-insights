package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.CandleStatsAvailableResponse;
import com.kisshore19.nseinsights.dto.response.CandleStatsAvailableResponse.PeriodEntry;
import com.kisshore19.nseinsights.dto.response.CandleStatsBuildResponse;
import com.kisshore19.nseinsights.dto.response.CandleStatsResponse;
import com.kisshore19.nseinsights.entity.CandleStats;
import com.kisshore19.nseinsights.repository.CandlePeriodProjection;
import com.kisshore19.nseinsights.repository.CandleStatsProjection;
import com.kisshore19.nseinsights.repository.CandleStatsRepository;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import com.kisshore19.nseinsights.util.PeriodKeyUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleStatsService {

    private final NseDailyPriceRepository priceRepository;
    private final CandleStatsRepository   statsRepository;

    private static final List<String> VALID_TIMEFRAMES =
            List.of("YEAR", "QUARTER", "MONTH", "WEEK");

    // =========================================================================
    // BUILD
    // =========================================================================

    /**
     * Builds candle stats for a specific period, a range, or all periods.
     *
     * For each period:
     *   1. Resolve periodKey → fromDate / toDate via PeriodKeyUtils
     *   2. Find symbols already in candle_stats for this period (skip set)
     *   3. Run ONE optimised CTE query scoped by date range (fast index hit)
     *   4. Filter out already-existing symbols
     *   5. Batch save new records
     *
     * No existing record is ever deleted or overwritten.
     */
    @Transactional
    public CandleStatsBuildResponse buildStats(
            String timeframe, String period, String from, String to) {

        validateTimeframe(timeframe);

        boolean hasPeriod = period != null && !period.isBlank();
        boolean hasFrom   = from   != null && !from.isBlank();
        boolean hasTo     = to     != null && !to.isBlank();

        if (hasPeriod && (hasFrom || hasTo)) {
            throw new IllegalArgumentException(
                    "Use either 'period' or 'from'+'to', not both.");
        }
        if (hasFrom != hasTo) {
            throw new IllegalArgumentException(
                    "'from' and 'to' must both be provided together.");
        }

        // ── Resolve period list ────────────────────────────────────────────────
        List<String> periodsToProcess;

        if (hasPeriod) {
            PeriodKeyUtils.validatePeriodKey(timeframe, period);
            periodsToProcess = List.of(period);

        } else if (hasFrom) {
            PeriodKeyUtils.validatePeriodKey(timeframe, from);
            PeriodKeyUtils.validatePeriodKey(timeframe, to);
            periodsToProcess = PeriodKeyUtils.periodKeysBetween(timeframe, from, to);

        } else {
            // No filter — all periods that exist in nse_daily_price
            periodsToProcess = priceRepository.findDistinctPeriodKeys(timeframe);
        }

        log.info("timeframe={} periods to process={}", timeframe, periodsToProcess.size());

        // ── Process each period ────────────────────────────────────────────────
        List<String> skipped  = new ArrayList<>();
        List<String> inserted = new ArrayList<>();
        int totalRecords      = 0;

        for (String pk : periodsToProcess) {

            // Step 1: symbols already done for this period — skip set
            Set<String> existingSymbols =
                    statsRepository.findExistingSymbolsForPeriod(timeframe, pk);

            // Step 2: resolve period key → date range
            LocalDate fromDate = PeriodKeyUtils.periodStart(timeframe, pk);
            LocalDate toDate   = PeriodKeyUtils.periodEnd(timeframe, pk);

            // Step 3: run single optimised CTE query — index hit on trade_date range
            List<CandleStatsProjection> projections =
                    priceRepository.findStatsForPeriod(fromDate, toDate);

            if (projections.isEmpty()) {
                log.warn("No data in nse_daily_price for period={} ({} to {})",
                        pk, fromDate, toDate);
                skipped.add(pk);
                continue;
            }

            // Step 4: filter out already-existing symbols
            List<CandleStats> toSave = projections.stream()
                    .filter(p -> !existingSymbols.contains(p.getSymbol()))
                    .map(p -> toEntity(p, timeframe, pk))
                    .toList();

            if (toSave.isEmpty()) {
                log.info("period={} — all {} symbols already exist, skipping",
                        pk, existingSymbols.size());
                skipped.add(pk);
                continue;
            }

            // Step 5: batch save
            statsRepository.saveAll(toSave);
            totalRecords += toSave.size();
            inserted.add(pk);

            log.info("period={} ({} to {}) — inserted={} skippedSymbols={}",
                    pk, fromDate, toDate, toSave.size(), existingSymbols.size());
        }

        log.info("Build complete — timeframe={} inserted={} skipped={} totalRecords={}",
                timeframe, inserted.size(), skipped.size(), totalRecords);

        return CandleStatsBuildResponse.builder()
                .timeframe(timeframe)
                .periodsRequested(periodsToProcess)
                .periodsSkipped(skipped)
                .periodsInserted(inserted)
                .recordsInserted(totalRecords)
                .build();
    }

    // =========================================================================
    // READ
    // =========================================================================

    public CandleStatsResponse getStats(String symbol, String timeframe, String periodKey) {
        validateTimeframe(timeframe);
        PeriodKeyUtils.validatePeriodKey(timeframe, periodKey);

        CandleStats stats = statsRepository
                .findBySymbolTimeframeAndPeriodKey(symbol, timeframe, periodKey)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No stats found for symbol=%s timeframe=%s period=%s"
                                .formatted(symbol, timeframe, periodKey)));
        return toResponse(stats);
    }

    // =========================================================================
    // AVAILABLE PERIODS
    // =========================================================================

    // =========================================================================
    // AVAILABLE PERIODS
    // =========================================================================

    public CandleStatsAvailableResponse getAvailablePeriods(String timeframe) {
        validateTimeframe(timeframe);

        // Repository returns CandlePeriodProjection — map to PeriodEntry DTO
        List<CandlePeriodProjection> projections = statsRepository.findAvailablePeriods(timeframe);

        List<PeriodEntry> periods = projections.stream()
                .map(p -> PeriodEntry.builder()
                        .periodKey(p.getPeriodKey())
                        .candleDate(p.getCandleDate())
                        .candleEndDate(p.getCandleEndDate())
                        .build())
                .toList();

        String from = periods.isEmpty() ? null : periods.get(0).getPeriodKey();
        String to   = periods.isEmpty() ? null : periods.get(periods.size() - 1).getPeriodKey();

        return CandleStatsAvailableResponse.builder()
                .timeframe(timeframe)
                .totalPeriods(periods.size())
                .from(from)
                .to(to)
                .periods(periods)
                .build();
    }

    // =========================================================================
    // MAPPING
    // =========================================================================

    private CandleStats toEntity(CandleStatsProjection p, String timeframe, String periodKey) {
        return CandleStats.builder()
                .symbol(p.getSymbol())
                .timeframe(timeframe)
                .periodKey(periodKey)
                .candleDate(p.getCandleDate())
                .candleEndDate(p.getCandleEndDate())
                .openPrice(p.getOpenPrice())
                .highPrice(p.getHighPrice())
                .lowPrice(p.getLowPrice())
                .lastPrice(p.getLastPrice())
                .highPriceDate(p.getHighPriceDate())
                .lowPriceDate(p.getLowPriceDate())
                .trend(p.getTrend())
                .highVolQty(p.getHighVolQty())
                .highVolDate(p.getHighVolDate())
                .lowVolQty(p.getLowVolQty())
                .lowVolDate(p.getLowVolDate())
                .highDelivQty(p.getHighDelivQty())
                .highDelivDate(p.getHighDelivDate())
                .lowDelivQty(p.getLowDelivQty())
                .lowDelivDate(p.getLowDelivDate())
                .build();
    }

    private CandleStatsResponse toResponse(CandleStats s) {
        return CandleStatsResponse.builder()
                .symbol(s.getSymbol())
                .timeframe(s.getTimeframe())
                .periodKey(s.getPeriodKey())
                .candleDate(s.getCandleDate())
                .candleEndDate(s.getCandleEndDate())
                .open(s.getOpenPrice())
                .high(s.getHighPrice())
                .low(s.getLowPrice())
                .lastPrice(s.getLastPrice())
                .highPriceDate(s.getHighPriceDate())
                .lowPriceDate(s.getLowPriceDate())
                .trend(s.getTrend())
                .highestVolume(CandleStatsResponse.VolumePoint.builder()
                        .date(s.getHighVolDate()).quantity(s.getHighVolQty()).build())
                .lowestVolume(CandleStatsResponse.VolumePoint.builder()
                        .date(s.getLowVolDate()).quantity(s.getLowVolQty()).build())
                .highestDelivery(CandleStatsResponse.VolumePoint.builder()
                        .date(s.getHighDelivDate()).quantity(s.getHighDelivQty()).build())
                .lowestDelivery(CandleStatsResponse.VolumePoint.builder()
                        .date(s.getLowDelivDate()).quantity(s.getLowDelivQty()).build())
                .build();
    }

    private void validateTimeframe(String timeframe) {
        if (!VALID_TIMEFRAMES.contains(timeframe)) {
            throw new IllegalArgumentException(
                    "Unsupported timeframe: %s. Valid: %s"
                            .formatted(timeframe, VALID_TIMEFRAMES));
        }
    }
}