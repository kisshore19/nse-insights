package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.service.IndexCandleStatsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/index-candles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IndexCandleStatsController {

    private final IndexCandleStatsService indexCandleStatsService;

    // ── POST /api/v1/index-candles/stats/build ────────────────────────────────
    // ?timeframe=YEAR                          → build ALL years
    // ?timeframe=YEAR&period=2024              → build 2024 only
    // ?timeframe=MONTH&from=2024-01&to=2024-06 → build Jan–Jun 2024
    @PostMapping("/stats/build")
    public ResponseEntity<ApiResponse<CandleStatsBuildResponse>> buildStats(
            @RequestParam                   String timeframe,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    indexCandleStatsService.buildStats(
                            timeframe.toUpperCase(), period, from, to)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400", ex.getMessage()));
        }
    }

    // ── GET /api/v1/index-candles/stats/available?timeframe=YEAR ─────────────
    @GetMapping("/stats/available")
    public ResponseEntity<ApiResponse<CandleStatsAvailableResponse>> availablePeriods(
            @RequestParam(defaultValue = "YEAR") String timeframe) {

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    indexCandleStatsService.getAvailablePeriods(timeframe.toUpperCase())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400", ex.getMessage()));
        }
    }

    // ── GET /api/v1/index-candles/{indexName}/stats?timeframe=YEAR&period=2024 ─
    // indexName should be URL-encoded if it contains spaces, e.g. "Nifty%2050"
    @GetMapping("/{indexName}/stats")
    public ResponseEntity<ApiResponse<IndexCandleStatsResponse>> getStats(
            @PathVariable                        String indexName,
            @RequestParam(defaultValue = "YEAR") String timeframe,
            @RequestParam                        String period) {

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    indexCandleStatsService.getStats(indexName, timeframe.toUpperCase(), period)));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("404", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400", ex.getMessage()));
        }
    }

    // ── GET /api/v1/index-candles/stats/screen ────────────────────────────────
    // sortBy: PCT_CHANGE | CLOSE | HIGH_VOL | PE | PB
    @GetMapping("/stats/screen")
    public ResponseEntity<ApiResponse<IndexScreenerResponse>> screen(
            @RequestParam                        String     timeframe,
            @RequestParam                        String     period,
            @RequestParam(required = false)      BigDecimal minClose,
            @RequestParam(required = false)      BigDecimal maxClose,
            @RequestParam(required = false)      BigDecimal minPctChange,
            @RequestParam(required = false)      BigDecimal maxPctChange,
            @RequestParam(required = false)      String     trend,
            @RequestParam(required = false)      BigDecimal minHighVol,
            @RequestParam(required = false)      String     highDatePos,
            @RequestParam(required = false)      String     lowDatePos,
            @RequestParam(defaultValue = "PCT_CHANGE") String sortBy,
            @RequestParam(defaultValue = "DESC")       String sortDir,
            @RequestParam(defaultValue = "0")          int    page,
            @RequestParam(defaultValue = "500")        int    size) {

        ScreenerRequest req = ScreenerRequest.builder()
                .timeframe(timeframe.toUpperCase())
                .period(period)
                .minClose(minClose)
                .maxClose(maxClose)
                .minPctChange(minPctChange)
                .maxPctChange(maxPctChange)
                .trend(trend != null ? trend.toUpperCase() : null)
                .minHighVol(minHighVol)
                .highDatePos(highDatePos)
                .lowDatePos(lowDatePos)
                .sortBy(sortBy.toUpperCase())
                .sortDir(sortDir.toUpperCase())
                .page(page)
                .size(size)
                .build();

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    indexCandleStatsService.screen(req)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("400", ex.getMessage()));
        }
    }
}
