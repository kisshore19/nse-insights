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
@CrossOrigin(origins = "*")  // Allow React frontend on different port
public class IngestionController {

    private final IngestionService ingestionService;

    // ── API 1: GET /available-dates ────────────────────────────────────────────
    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<AvailableDatesResponse>> getAvailableDates() {
        log.info("GET /available-dates called");
        AvailableDatesResponse response = ingestionService.getAvailableDates();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── API 2: POST /download ──────────────────────────────────────────────────
    @PostMapping("/download")
    public ResponseEntity<ApiResponse<DownloadResponse>> download(
            @Valid @RequestBody DownloadRequest request) {
        log.info("POST /download called for date: {}", request.getTradeDate());
        DownloadResponse response = ingestionService.downloadAndStore(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── API 3: GET /history ────────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<DownloadHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "ALL") String status) {
        DownloadHistoryResponse response = ingestionService.getHistory(page, size, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── API 4: GET /status/{tradeDate} ─────────────────────────────────────────
    @GetMapping("/status/{tradeDate}")
    public ResponseEntity<ApiResponse<DownloadStatusResponse>> getStatus(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        DownloadStatusResponse response = ingestionService.getStatus(tradeDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── API 5: DELETE /{tradeDate} ─────────────────────────────────────────────
    @DeleteMapping("/{tradeDate}")
    public ResponseEntity<ApiResponse<Void>> deleteByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        ingestionService.deleteByDate(tradeDate);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status("SUCCESS")
                .message("Data for " + tradeDate + " deleted successfully.")
                .build());
    }

    // ── API 6: GET /summary ────────────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IngestionSummaryResponse>> getSummary() {
        IngestionSummaryResponse response = ingestionService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
