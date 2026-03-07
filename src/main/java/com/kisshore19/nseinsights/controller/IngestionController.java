package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.request.DownloadRequest;
import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.service.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IngestionController {

    private final IngestionService ingestionService;

    // ── POST /download ─────────────────────────────────────────────────────────
    // Single date:  { "fromTradeDate": "2025-03-05" }
    // Range:        { "fromTradeDate": "2025-03-01", "toTradeDate": "2025-03-05" }
    @PostMapping("/download")
    public ResponseEntity<ApiResponse<RangeDownloadResponse>> download(
            @Valid @RequestBody DownloadRequest request) {

        log.info("POST /download — from: {} to: {}, overwrite: {}",
                request.getFromTradeDate(),
                request.getEffectiveToDate(),
                request.isOverwrite());

        RangeDownloadResponse response = ingestionService.downloadRange(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /history ───────────────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<DownloadHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "ALL") String status) {
        return ResponseEntity.ok(ApiResponse.success(
                ingestionService.getHistory(page, size, status)));
    }

    // ── GET /status/{tradeDate} ────────────────────────────────────────────────
    @GetMapping("/status/{tradeDate}")
    public ResponseEntity<ApiResponse<DownloadStatusResponse>> getStatus(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return ResponseEntity.ok(ApiResponse.success(
                ingestionService.getStatus(tradeDate)));
    }

    // ── GET /available-dates ───────────────────────────────────────────────────
    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<AvailableDatesResponse>> getAvailableDates() {
        return ResponseEntity.ok(ApiResponse.success(
                ingestionService.getAvailableDates()));
    }

    // ── DELETE /{tradeDate} ────────────────────────────────────────────────────
    @DeleteMapping("/{tradeDate}")
    public ResponseEntity<ApiResponse<Void>> deleteByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        ingestionService.deleteByDate(tradeDate);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("SUCCESS")
                .message("Data for " + tradeDate + " deleted successfully.")
                .build());
    }

    // ── GET /summary ───────────────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IngestionSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                ingestionService.getSummary()));
    }
}