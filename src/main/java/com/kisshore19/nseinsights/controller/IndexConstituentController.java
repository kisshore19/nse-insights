package com.kisshore19.nseinsights.controller;

import com.kisshore19.nseinsights.dto.response.ApiResponse;
import com.kisshore19.nseinsights.dto.response.IndexListResponse;
import com.kisshore19.nseinsights.dto.response.IndexSyncResponse;
import com.kisshore19.nseinsights.service.IndexIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for syncing NSE index constituents into index_master.
 * Run POST /sync once (or whenever index composition changes) to populate the table.
 */
@RestController
@RequestMapping("/api/v1/index-constituents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IndexConstituentController {

    private final IndexIngestionService indexIngestionService;

    /**
     * POST /api/v1/index-constituents/sync
     * Download and upsert constituents for ALL supported indices from NSE.
     *
     * Query Parameters:
     * - force: true to bypass cache and re-download (default: false)
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<IndexSyncResponse>> syncAll(
            @RequestParam(defaultValue = "false") boolean force) {

        log.info("POST /index-constituents/sync - force={}", force);
        return ResponseEntity.ok(ApiResponse.success(
                indexIngestionService.syncAll(force)));
    }

    /**
     * POST /api/v1/index-constituents/sync/{indexName}
     * Download and upsert constituents for a single index from NSE.
     *
     * Path:  indexName - e.g. NIFTY50, NIFTYBANK, NIFTY100
     * Query: force     - true to bypass cache (default: false)
     */
    @PostMapping("/sync/{indexName}")
    public ResponseEntity<ApiResponse<IndexSyncResponse>> syncOne(
            @PathVariable String indexName,
            @RequestParam(defaultValue = "false") boolean force) {

        log.info("POST /index-constituents/sync/{} - force={}", indexName, force);
        return ResponseEntity.ok(ApiResponse.success(
                indexIngestionService.syncIndices(List.of(indexName.toUpperCase()), force)));
    }

    /**
     * GET /api/v1/index-constituents/list
     * Returns synced indices (with constituent counts) and the full supported list.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<IndexListResponse>> list() {
        log.info("GET /index-constituents/list");
        return ResponseEntity.ok(ApiResponse.success(
                indexIngestionService.getIndexList()));
    }
}
