package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.service.CandleStatsService;
import com.kisshore19.nseinsights.service.ScreenerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/candles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CandleStatsController {

    private final CandleStatsService candleStatsService;
    private final ScreenerService screenerService;
    // ── POST /api/v1/candles/stats/build ─────────────────────────────────────
    //
    // Build stats for a specific period, a range, or all periods.
    //
    // Examples:
    //   ?timeframe=YEAR                          → build ALL years
    //   ?timeframe=YEAR&period=2024              → build 2024 only
    //   ?timeframe=MONTH&from=2024-01&to=2024-06 → build Jan–Jun 2024
    //   ?timeframe=WEEK&period=2024-W03          → build week 3 of 2024
    //
    // Skip logic: existing records are never deleted or overwritten.
    @PostMapping("/stats/build")
    public ResponseEntity<ApiResponse<CandleStatsBuildResponse>> buildStats(
            @RequestParam                        String timeframe,
            @RequestParam(required = false)      String period,
            @RequestParam(required = false)      String from,
            @RequestParam(required = false)      String to) {

        try {
            CandleStatsBuildResponse result = candleStatsService.buildStats(
                    timeframe.toUpperCase(),
                    period,
                    from,
                    to);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("404",ex.getMessage()));
        }
    }

    // ── GET /api/v1/candles/stats/available?timeframe=WEEK ───────────────────
    //
    // Returns all period keys that already have stats built, with their
    // exact candle_date and candle_end_date ranges.
    // Call this first to find valid period keys before using the build or get APIs.
    @GetMapping("/stats/available")
    public ResponseEntity<ApiResponse<CandleStatsAvailableResponse>> availablePeriods(
            @RequestParam(defaultValue = "YEAR") String timeframe) {

        try {
            CandleStatsAvailableResponse response =
                    candleStatsService.getAvailablePeriods(timeframe.toUpperCase());
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("500",ex.getMessage()));
        }
    }

    // ── GET /api/v1/candles/{symbol}/stats?timeframe=YEAR&period=2024 ────────
    //
    // Fetch saved stats for a symbol + timeframe + period key.
    // Use /stats/available first to find valid period keys.
    //
    // period format:
    //   YEAR    → "2024"
    //   QUARTER → "2024-Q1"
    //   MONTH   → "2024-01"
    //   WEEK    → "2024-W03"
    @GetMapping("/{symbol}/stats")
    public ResponseEntity<ApiResponse<CandleStatsResponse>> getStats(
            @PathVariable                        String symbol,
            @RequestParam(defaultValue = "YEAR") String timeframe,
            @RequestParam                        String period) {

        try {
            CandleStatsResponse response = candleStatsService.getStats(
                    symbol.toUpperCase(),
                    timeframe.toUpperCase(),
                    period);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("400", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400",ex.getMessage()));
        }
    }

    // ── GET /api/v1/candles/stats/screen ─────────────────────────────────────────
// Reads from candle_stats table (pre-built data) — no new processing.
// Returns all symbols for a timeframe + period. UI does all filtering client-side.
    @GetMapping("stats/screen")
    public ResponseEntity<ApiResponse<ScreenerResponse>> screen(

            @RequestParam                        String     timeframe,
            @RequestParam                        String     period,

            // Optional filters — used when calling from Postman, ignored by screener UI
            @RequestParam(required = false) BigDecimal minClose,
            @RequestParam(required = false)      BigDecimal maxClose,
            @RequestParam(required = false)      BigDecimal minPctChange,
            @RequestParam(required = false)      BigDecimal maxPctChange,
            @RequestParam(required = false)      String     trend,
            @RequestParam(required = false)      BigDecimal minHighVol,
            @RequestParam(required = false)      BigDecimal minHighDeliv,
            @RequestParam(required = false)      BigDecimal minDelivPct,
            @RequestParam(required = false)      String     highDatePos,
            @RequestParam(required = false)      String     lowDatePos,

            @RequestParam(defaultValue = "PCT_CHANGE") String sortBy,
            @RequestParam(defaultValue = "DESC")       String sortDir,
            @RequestParam(defaultValue = "0")          int    page,
            @RequestParam(defaultValue = "2500")       int    size   // default large so UI gets everything
    ) {
        ScreenerRequest req = ScreenerRequest.builder()
                .timeframe(timeframe.toUpperCase())
                .period(period)
                .minClose(minClose)
                .maxClose(maxClose)
                .minPctChange(minPctChange)
                .maxPctChange(maxPctChange)
                .trend(trend != null ? trend.toUpperCase() : null)
                .minHighVol(minHighVol)
                .minHighDeliv(minHighDeliv)
                .minDelivPct(minDelivPct)
                .highDatePos(highDatePos)
                .lowDatePos(lowDatePos)
                .sortBy(sortBy.toUpperCase())
                .sortDir(sortDir.toUpperCase())
                .page(page)
                .size(size)
                .build();

        try {
            ScreenerResponse response = screenerService.screen(req);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400", ex.getMessage()));
        }
    }
}