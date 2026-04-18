package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.dto.response.ScreenerResponse;
import com.kisshore19.nseinsights.dto.response.ScreenerResponse.ScreenerResult;
import com.kisshore19.nseinsights.entity.CandleStats;
import com.kisshore19.nseinsights.repository.CandleStatsRepository;
import com.kisshore19.nseinsights.repository.CandleStatsSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreenerService {

    private final CandleStatsRepository statsRepository;

    private static final int MAX_SIZE = 50000;

    /**
     * Screens candle_stats rows using the provided filters.
     * All filtering is done by JPA Specification — no raw SQL.
     * pctChange and delivPct are computed in Java after fetch.
     */
    public ScreenerResponse screen(ScreenerRequest req) {

        // Clamp page size
        int size = Math.min(req.getSize(), MAX_SIZE);
        req.setSize(size);

        // Build Specification from request filters
        CandleStatsSpecification spec = new CandleStatsSpecification(req);

        // Build Sort
        Sort sort = buildSort(req.getSortBy(), req.getSortDir(), req);

        // Build Pageable
        Pageable pageable = PageRequest.of(req.getPage(), size, sort);

        // Execute — single query on candle_stats, no joins
        Page<CandleStats> page = statsRepository.findAll(spec, pageable);

        log.info("Screener — timeframe={} period={} matched={} page={}/{}",
                req.getTimeframe(), req.getPeriod(),
                page.getTotalElements(), req.getPage(), page.getTotalPages());

        // Map entities → response results (compute pctChange + delivPct in Java)
        List<ScreenerResult> results = page.getContent().stream()
                .map(this::toResult)
                .toList();

        return ScreenerResponse.builder()
                .timeframe(req.getTimeframe())
                .period(req.getPeriod())
                .totalMatched(page.getTotalElements())
                .page(req.getPage())
                .size(size)
                .results(results)
                .build();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private ScreenerResult toResult(CandleStats s) {
        BigDecimal pctChange = computePctChange(s.getOpenPrice(), s.getLastPrice());
        BigDecimal delivPct  = computeDelivPct(s.getHighDelivQty(), s.getHighVolQty());

        return ScreenerResult.builder()
                .symbol(s.getSymbol())
                .candleDate(s.getCandleDate())
                .candleEndDate(s.getCandleEndDate())
                .open(s.getOpenPrice())
                .close(s.getLastPrice())
                .high(s.getHighPrice())
                .low(s.getLowPrice())
                .pctChange(pctChange)
                .delivPct(delivPct)
                .trend(s.getTrend())
                .highPriceDate(s.getHighPriceDate())
                .lowPriceDate(s.getLowPriceDate())
                .highVolQty(s.getHighVolQty())
                .highVolDate(s.getHighVolDate())
                .lowVolQty(s.getLowVolQty())
                .lowVolDate(s.getLowVolDate())
                .highDelivQty(s.getHighDelivQty())
                .highDelivDate(s.getHighDelivDate())
                .lowDelivQty(s.getLowDelivQty())
                .lowDelivDate(s.getLowDelivDate())
                .build();
    }

    // ── Computed fields ───────────────────────────────────────────────────────

    private BigDecimal computePctChange(BigDecimal open, BigDecimal close) {
        if (open == null || close == null || open.compareTo(BigDecimal.ZERO) == 0) return null;
        return close.subtract(open)
                    .divide(open, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeDelivPct(Long highDeliv, Long highVol) {
        if (highDeliv == null || highVol == null || highVol == 0) return null;
        return BigDecimal.valueOf(highDeliv)
                         .divide(BigDecimal.valueOf(highVol), 6, RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(100))
                         .setScale(2, RoundingMode.HALF_UP);
    }

    // ── Sort builder ──────────────────────────────────────────────────────────

    private Sort buildSort(String sortBy, String sortDir, ScreenerRequest req) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        // For computed fields (pctChange, delivPct) we can't sort in DB
        // because they're not stored columns — sort by closest stored proxy instead
        String col = switch (sortBy == null ? "PCT_CHANGE" : sortBy.toUpperCase()) {
            case "CLOSE"      -> "lastPrice";
            case "HIGH_VOL"   -> "highVolQty";
            case "HIGH_DELIV" -> "highDelivQty";
            // PCT_CHANGE proxy: sort by lastPrice (higher close = more likely high pctChange)
            // DELIV_PCT proxy: sort by highDelivQty
            case "DELIV_PCT"  -> "highDelivQty";
            default           -> "lastPrice";   // PCT_CHANGE proxy
        };

        return Sort.by(dir, col);
    }
}