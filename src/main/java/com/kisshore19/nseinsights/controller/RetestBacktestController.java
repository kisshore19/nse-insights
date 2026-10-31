package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.response.ApiResponse;
import com.kisshore19.nseinsights.dto.response.RetestBacktestResponse;
import com.kisshore19.nseinsights.service.RetestBacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/backtest")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RetestBacktestController {

    private final RetestBacktestService service;

    // GET /api/v1/backtest/retest?from=2023-Q1&to=2026-Q3&source=VOL&side=BOTH&requireBreakout=true
    //
    // Runs the previous-quarter volume level retest strategy over history. Nothing is stored.
    // from / to are BASE quarters; each is traded in the quarter that follows it.
    @GetMapping("/retest")
    public ResponseEntity<ApiResponse<RetestBacktestResponse>> retest(
            @RequestParam                              String from,
            @RequestParam                              String to,
            @RequestParam(defaultValue = "VOL")        String source,
            @RequestParam(defaultValue = "BOTH")       String side,
            @RequestParam(defaultValue = "false")      boolean requireBreakout,
            @RequestParam(defaultValue = "60")         int lookback,
            @RequestParam(defaultValue = "0")          double stopBufferPct,
            @RequestParam(defaultValue = "0")          int maxHoldDays,
            @RequestParam(defaultValue = "0")          double minRR,
            @RequestParam(defaultValue = "LEVEL_RETEST") String strategy,
            @RequestParam(defaultValue = "EXTREME")    String targetMode,
            @RequestParam(defaultValue = "2")          double targetR,
            @RequestParam(defaultValue = "5")          int minDays,
            @RequestParam(defaultValue = "VOLUME")     String signal,
            @RequestParam(defaultValue = "NONE")       String indexMode,
            @RequestParam(defaultValue = "ALL")        String indexScope) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.run(new RetestBacktestService.Params(
                    from, to, source.toUpperCase(), side.toUpperCase(), requireBreakout,
                    lookback, stopBufferPct, maxHoldDays, minRR,
                    strategy.toUpperCase(), targetMode.toUpperCase(), targetR, minDays, signal.toUpperCase(), indexMode.toUpperCase(), indexScope.toUpperCase()))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("400", ex.getMessage()));
        }
    }
}
