package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.service.DataExplorerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller for Module 2 - Data Explorer APIs.
 * Provides endpoints for stock search, filtering, and analytics.
 */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StockExplorerController {

    private final DataExplorerService dataExplorerService;

    /**
     * API 1: GET /api/v1/stocks
     * Advanced search with filters and pagination
     *
     * Query Parameters:
     * - date: Trade date (optional, defaults to latest)
     * - index: Index name e.g., NIFTY50 (optional, not yet implemented)
     * - sector: Sector filter (optional, not yet implemented)
     * - minPrice: Minimum close price (optional)
     * - maxPrice: Maximum close price (optional)
     * - minVolume: Minimum traded quantity (optional)
     * - minPctChange: Minimum percentage change (optional)
     * - maxPctChange: Maximum percentage change (optional)
     * - minDeliveryPct: Minimum delivery percentage (optional)
     * - page: Page number, 0-indexed (default: 0)
     * - size: Page size (default: 50)
     * - sortBy: Field to sort by - pctChange, closePrice, tradedQuantity (default: tradeDate)
     * - sortDir: Sort direction - ASC or DESC (default: DESC)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<StockSearchResponse>> searchStocks(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String index,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long minVolume,
            @RequestParam(required = false) BigDecimal minPctChange,
            @RequestParam(required = false) BigDecimal maxPctChange,
            @RequestParam(required = false) BigDecimal minDeliveryPct,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "tradeDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("GET /api/v1/stocks - Search with filters: date={}, minPrice={}, maxPrice={}, " +
                "minVolume={}, page={}, size={}", date, minPrice, maxPrice, minVolume, page, size);

        // Build sort order
        Sort.Direction direction = Sort.Direction.fromString(sortDir.toUpperCase());
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Call service (service will parse date string in dd-MM-yyyy format)
        StockSearchResponse response = dataExplorerService.searchStocks(
                date, null, minPrice, maxPrice, minVolume, minPctChange, maxPctChange, minDeliveryPct, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 2: GET /api/v1/stocks/dates
     * Get list of all available trading dates in the database
     */
    @GetMapping("/dates")
    public ResponseEntity<ApiResponse<AvailableDatesResponse>> getAvailableDates() {
        log.info("GET /api/v1/stocks/dates - Fetching all available trading dates");

        AvailableDatesResponse response = dataExplorerService.getAvailableDates();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 3: GET /api/v1/stocks/{symbol}
     * Get detailed stock information for a specific date
     *
     * Path Parameters:
     * - symbol: Stock symbol (e.g., INFY, RELIANCE)
     *
     * Query Parameters:
     * - date: Trade date in dd-MM-yyyy format (e.g., 15-01-2025) - optional, defaults to latest
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<StockDetailResponse>> getStockDetail(
            @PathVariable String symbol,
            @RequestParam(required = false) String date) {

        log.info("GET /api/v1/stocks/{} - Fetching stock detail for date: {}", symbol, date);

        StockDetailResponse response = dataExplorerService.getStockDetail(symbol, date);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 4: GET /api/v1/stocks/sectors
     * Get list of all distinct sectors from index master
     */
    @GetMapping("/sectors")
    public ResponseEntity<ApiResponse<SectorsResponse>> getSectors() {
        log.info("GET /api/v1/stocks/sectors - Fetching all distinct sectors");

        SectorsResponse response = dataExplorerService.getSectors();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 5: GET /api/v1/stocks/top-gainers
     * Get top gainers for a specific date
     *
     * Query Parameters:
     * - date: Trade date in dd-MM-yyyy format (e.g., 15-01-2025) - optional, defaults to latest
     * - limit: Number of top gainers to return (default: 10)
     */
    @GetMapping("/top-gainers")
    public ResponseEntity<ApiResponse<TopMoversResponse>> getTopGainers(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/v1/stocks/top-gainers - date: {}, limit: {}", date, limit);

        TopMoversResponse response = dataExplorerService.getTopGainers(date, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 6: GET /api/v1/stocks/top-losers
     * Get top losers for a specific date
     *
     * Query Parameters:
     * - date: Trade date in dd-MM-yyyy format (e.g., 15-01-2025) - optional, defaults to latest
     * - limit: Number of top losers to return (default: 10)
     */
    @GetMapping("/top-losers")
    public ResponseEntity<ApiResponse<TopMoversResponse>> getTopLosers(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/v1/stocks/top-losers - date: {}, limit: {}", date, limit);

        TopMoversResponse response = dataExplorerService.getTopLosers(date, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
