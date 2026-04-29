package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.request.DownloadRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.service.IndexCloseIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/index-ingestion")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IndexIngestionController {

    private final IndexCloseIngestionService indexCloseIngestionService;

    // ── POST /download ────────────────────────────────────────────────────────
    // Single date:  { "fromTradeDate": "2026-04-28" }
    // Range:        { "fromTradeDate": "2026-04-01", "toTradeDate": "2026-04-28" }
    // Re-download:  { "fromTradeDate": "2026-04-28", "overwrite": true }
    @PostMapping("/download")
    public ResponseEntity<ApiResponse<RangeDownloadResponse>> download(
            @Valid @RequestBody DownloadRequest request) {

        log.info("POST /index-ingestion/download — from: {} to: {}, overwrite: {}",
                request.getFromTradeDate(), request.getEffectiveToDate(), request.isOverwrite());

        return ResponseEntity.ok(ApiResponse.success(
                indexCloseIngestionService.downloadRange(request)));
    }

    // ── GET /available-dates ──────────────────────────────────────────────────
    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<AvailableDatesResponse>> getAvailableDates() {
        return ResponseEntity.ok(ApiResponse.success(
                indexCloseIngestionService.getAvailableDates()));
    }

    // ── GET /status/{tradeDate} ───────────────────────────────────────────────
    @GetMapping("/status/{tradeDate}")
    public ResponseEntity<ApiResponse<DownloadStatusResponse>> getStatus(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return ResponseEntity.ok(ApiResponse.success(
                indexCloseIngestionService.getStatus(tradeDate)));
    }

    // ── DELETE /{tradeDate} ───────────────────────────────────────────────────
    @DeleteMapping("/{tradeDate}")
    public ResponseEntity<ApiResponse<Void>> deleteByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {

        indexCloseIngestionService.deleteByDate(tradeDate);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("SUCCESS")
                .message("Index close data for " + tradeDate + " deleted successfully.")
                .build());
    }

    // ── GET /summary ──────────────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IngestionSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                indexCloseIngestionService.getSummary()));
    }
}
