package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.response.ApiResponse;
import com.kisshore19.nseinsights.dto.response.DeliverySequenceResponse;
import com.kisshore19.nseinsights.service.DeliverySequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/delivery-sequences")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeliverySequenceController {

    private final DeliverySequenceService service;

    // GET /api/v1/delivery-sequences/RELIANCE?from=2024-01-01&to=2026-10-30
    //
    // Delivery rise -> cool-off -> next rise sequences for one symbol, with sequence-to-sequence comparison.
    // Computed on demand from nse_daily_price; nothing is stored.
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<DeliverySequenceResponse>> analyse(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.run(new DeliverySequenceService.Params(
                    symbol, from, to))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("400", ex.getMessage()));
        }
    }
}
