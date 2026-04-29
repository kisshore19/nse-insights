package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.dto.response.CandleStatsAvailableResponse.PeriodEntry;
import com.kisshore19.nseinsights.dto.response.IndexScreenerResponse.IndexScreenerResult;
import com.kisshore19.nseinsights.entity.IndexCandleStats;
import com.kisshore19.nseinsights.repository.*;
import com.kisshore19.nseinsights.util.PeriodKeyUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexCandleStatsService {

    private final IndexCandleStatsRepository indexStatsRepository;

    private static final List<String> VALID_TIMEFRAMES =
            List.of("YEAR", "QUARTER", "MONTH", "WEEK");

    private static final int MAX_SIZE = 10_000;

    // =========================================================================
    // BUILD
    // =========================================================================

    @Transactional
    public CandleStatsBuildResponse buildStats(
            String timeframe, String period, String from, String to) {

        validateTimeframe(timeframe);

        boolean hasPeriod = period != null && !period.isBlank();
        boolean hasFrom   = from   != null && !from.isBlank();
        boolean hasTo     = to     != null && !to.isBlank();

        if (hasPeriod && (hasFrom || hasTo)) {
            throw new IllegalArgumentException("Use either 'period' or 'from'+'to', not both.");
        }
        if (hasFrom != hasTo) {
            throw new IllegalArgumentException("'from' and 'to' must both be provided together.");
        }

        // Resolve period list
        List<String> periodsToProcess;
        if (hasPeriod) {
            PeriodKeyUtils.validatePeriodKey(timeframe, period);
            periodsToProcess = List.of(period);
        } else if (hasFrom) {
            PeriodKeyUtils.validatePeriodKey(timeframe, from);
            PeriodKeyUtils.validatePeriodKey(timeframe, to);
            periodsToProcess = PeriodKeyUtils.periodKeysBetween(timeframe, from, to);
        } else {
            periodsToProcess = indexStatsRepository.findDistinctPeriodKeys(timeframe);
        }

        log.info("Index stats build — timeframe={} periods={}", timeframe, periodsToProcess.size());

        List<String> skipped  = new ArrayList<>();
        List<String> inserted = new ArrayList<>();
        int totalRecords      = 0;

        for (String pk : periodsToProcess) {
            Set<String> existingIndices =
                    indexStatsRepository.findExistingIndicesForPeriod(timeframe, pk);

            LocalDate fromDate = PeriodKeyUtils.periodStart(timeframe, pk);
            LocalDate toDate   = PeriodKeyUtils.periodEnd(timeframe, pk);

            List<IndexCandleStatsProjection> projections =
                    indexStatsRepository.findIndexStatsForPeriod(fromDate, toDate);

            if (projections.isEmpty()) {
                log.warn("No index_daily_close data for period={} ({} to {})", pk, fromDate, toDate);
                skipped.add(pk);
                continue;
            }

            List<IndexCandleStats> toSave = projections.stream()
                    .filter(p -> !existingIndices.contains(p.getIndexName()))
                    .map(p -> toEntity(p, timeframe, pk))
                    .toList();

            if (toSave.isEmpty()) {
                log.info("period={} — all {} indices already exist, skipping", pk, existingIndices.size());
                skipped.add(pk);
                continue;
            }

            indexStatsRepository.saveAll(toSave);
            totalRecords += toSave.size();
            inserted.add(pk);

            log.info("period={} ({} to {}) — inserted={} skippedIndices={}",
                    pk, fromDate, toDate, toSave.size(), existingIndices.size());
        }

        return CandleStatsBuildResponse.builder()
                .timeframe(timeframe)
                .periodsRequested(periodsToProcess)
                .periodsSkipped(skipped)
                .periodsInserted(inserted)
                .recordsInserted(totalRecords)
                .build();
    }

    // =========================================================================
    // READ — single record
    // =========================================================================

    public IndexCandleStatsResponse getStats(String indexName, String timeframe, String periodKey) {
        validateTimeframe(timeframe);
        PeriodKeyUtils.validatePeriodKey(timeframe, periodKey);

        IndexCandleStats stats = indexStatsRepository
                .findByIndexNameTimeframeAndPeriodKey(indexName, timeframe, periodKey)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No stats found for indexName=%s timeframe=%s period=%s"
                                .formatted(indexName, timeframe, periodKey)));
        return toResponse(stats);
    }

    // =========================================================================
    // AVAILABLE PERIODS
    // =========================================================================

    public CandleStatsAvailableResponse getAvailablePeriods(String timeframe) {
        validateTimeframe(timeframe);

        List<CandlePeriodProjection> projections =
                indexStatsRepository.findAvailablePeriods(timeframe);

        List<PeriodEntry> periods = projections.stream()
                .map(p -> PeriodEntry.builder()
                        .periodKey(p.getPeriodKey())
                        .candleDate(p.getCandleDate())
                        .candleEndDate(p.getCandleEndDate())
                        .build())
                .toList();

        String first = periods.isEmpty() ? null : periods.get(0).getPeriodKey();
        String last  = periods.isEmpty() ? null : periods.get(periods.size() - 1).getPeriodKey();

        return CandleStatsAvailableResponse.builder()
                .timeframe(timeframe)
                .totalPeriods(periods.size())
                .from(first).to(last)
                .periods(periods)
                .build();
    }

    // =========================================================================
    // SCREEN
    // =========================================================================

    public IndexScreenerResponse screen(ScreenerRequest req) {
        int size = Math.min(req.getSize(), MAX_SIZE);
        req.setSize(size);

        IndexCandleStatsSpecification spec = new IndexCandleStatsSpecification(req);

        Sort sort = buildSort(req.getSortBy(), req.getSortDir());
        Page<IndexCandleStats> page = indexStatsRepository.findAll(
                spec, PageRequest.of(req.getPage(), size, sort));

        log.info("Index screener — timeframe={} period={} matched={}",
                req.getTimeframe(), req.getPeriod(), page.getTotalElements());

        List<IndexScreenerResult> results = page.getContent().stream()
                .map(this::toScreenerResult)
                .toList();

        return IndexScreenerResponse.builder()
                .timeframe(req.getTimeframe())
                .period(req.getPeriod())
                .totalMatched(page.getTotalElements())
                .page(req.getPage())
                .size(size)
                .results(results)
                .build();
    }

    // =========================================================================
    // MAPPING
    // =========================================================================

    private IndexCandleStats toEntity(IndexCandleStatsProjection p, String timeframe, String pk) {
        return IndexCandleStats.builder()
                .indexName(p.getIndexName())
                .timeframe(timeframe)
                .periodKey(pk)
                .candleDate(p.getCandleDate())
                .candleEndDate(p.getCandleEndDate())
                .openValue(p.getOpenValue())
                .highValue(p.getHighValue())
                .lowValue(p.getLowValue())
                .lastValue(p.getLastValue())
                .trend(p.getTrend())
                .highValueDate(p.getHighValueDate())
                .lowValueDate(p.getLowValueDate())
                .highVolQty(p.getHighVolQty())
                .highVolDate(p.getHighVolDate())
                .lowVolQty(p.getLowVolQty())
                .lowVolDate(p.getLowVolDate())
                .avgPe(p.getAvgPe())
                .avgPb(p.getAvgPb())
                .avgDivYield(p.getAvgDivYield())
                .build();
    }

    private IndexCandleStatsResponse toResponse(IndexCandleStats s) {
        return IndexCandleStatsResponse.builder()
                .indexName(s.getIndexName())
                .timeframe(s.getTimeframe())
                .periodKey(s.getPeriodKey())
                .candleDate(s.getCandleDate())
                .candleEndDate(s.getCandleEndDate())
                .open(s.getOpenValue())
                .high(s.getHighValue())
                .low(s.getLowValue())
                .lastValue(s.getLastValue())
                .trend(s.getTrend())
                .highValueDate(s.getHighValueDate())
                .lowValueDate(s.getLowValueDate())
                .highestVolume(IndexCandleStatsResponse.VolumePoint.builder()
                        .date(s.getHighVolDate()).quantity(s.getHighVolQty()).build())
                .lowestVolume(IndexCandleStatsResponse.VolumePoint.builder()
                        .date(s.getLowVolDate()).quantity(s.getLowVolQty()).build())
                .avgPe(s.getAvgPe())
                .avgPb(s.getAvgPb())
                .avgDivYield(s.getAvgDivYield())
                .build();
    }

    private IndexScreenerResult toScreenerResult(IndexCandleStats s) {
        return IndexScreenerResult.builder()
                .indexName(s.getIndexName())
                .candleDate(s.getCandleDate())
                .candleEndDate(s.getCandleEndDate())
                .open(s.getOpenValue())
                .close(s.getLastValue())
                .high(s.getHighValue())
                .low(s.getLowValue())
                .pctChange(computePctChange(s.getOpenValue(), s.getLastValue()))
                .trend(s.getTrend())
                .highValueDate(s.getHighValueDate())
                .lowValueDate(s.getLowValueDate())
                .highVolQty(s.getHighVolQty())
                .highVolDate(s.getHighVolDate())
                .lowVolQty(s.getLowVolQty())
                .lowVolDate(s.getLowVolDate())
                .avgPe(s.getAvgPe())
                .avgPb(s.getAvgPb())
                .avgDivYield(s.getAvgDivYield())
                .build();
    }

    private BigDecimal computePctChange(BigDecimal open, BigDecimal close) {
        if (open == null || close == null || open.compareTo(BigDecimal.ZERO) == 0) return null;
        return close.subtract(open)
                .divide(open, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        String col = switch (sortBy == null ? "PCT_CHANGE" : sortBy.toUpperCase()) {
            case "CLOSE"    -> "lastValue";
            case "HIGH_VOL" -> "highVolQty";
            case "PE"       -> "avgPe";
            case "PB"       -> "avgPb";
            default         -> "lastValue";   // PCT_CHANGE proxy
        };

        return Sort.by(dir, col);
    }

    private void validateTimeframe(String timeframe) {
        if (!VALID_TIMEFRAMES.contains(timeframe)) {
            throw new IllegalArgumentException(
                    "Unsupported timeframe: %s. Valid: %s".formatted(timeframe, VALID_TIMEFRAMES));
        }
    }
}
