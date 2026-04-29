package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.IndexListResponse;
import com.kisshore19.nseinsights.dto.response.IndexListResponse.IndexInfo;
import com.kisshore19.nseinsights.dto.response.IndexSyncResponse;
import com.kisshore19.nseinsights.dto.response.IndexSyncResult;
import com.kisshore19.nseinsights.entity.IndexMaster;
import com.kisshore19.nseinsights.repository.IndexMasterRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexIngestionService {

    private final NseIndexDownloader     indexDownloader;
    private final IndexCsvParserService  indexCsvParser;
    private final IndexMasterRepository  indexMasterRepository;

    // ── Sync all supported indices ─────────────────────────────────────────────
    public IndexSyncResponse syncAll(boolean forceRefresh) {
        return syncIndices(NseIndexDownloader.getSupportedIndexNames(), forceRefresh);
    }

    // ── Sync a specific list of indices ───────────────────────────────────────
    public IndexSyncResponse syncIndices(List<String> indexNames, boolean forceRefresh) {
        long startTime = System.currentTimeMillis();

        List<IndexSyncResult> results = indexNames.stream()
                .map(name -> syncSingleIndex(name.toUpperCase(), forceRefresh))
                .collect(Collectors.toList());

        int totalAdded       = results.stream().mapToInt(IndexSyncResult::getAddedCount).sum();
        int totalUpdated     = results.stream().mapToInt(IndexSyncResult::getUpdatedCount).sum();
        int totalDeactivated = results.stream().mapToInt(IndexSyncResult::getDeactivatedCount).sum();

        return IndexSyncResponse.builder()
                .syncedIndices(indexNames.size())
                .totalAdded(totalAdded)
                .totalUpdated(totalUpdated)
                .totalDeactivated(totalDeactivated)
                .timeTakenMs(System.currentTimeMillis() - startTime)
                .results(results)
                .build();
    }

    // ── Sync a single index with upsert logic ─────────────────────────────────
    @Transactional
    public IndexSyncResult syncSingleIndex(String indexName, boolean forceRefresh) {
        log.info("Syncing index: {}", indexName);
        long startTime = System.currentTimeMillis();

        try {
            if (forceRefresh) {
                indexDownloader.invalidateCache(indexName);
            }

            NseIndexDownloader.IndexDownloadResult downloadResult =
                    indexDownloader.download(indexName);

            List<IndexMaster> incoming =
                    indexCsvParser.parse(downloadResult.getCsvContent(), indexName);

            if (incoming.isEmpty()) {
                throw new RuntimeException("No constituent records parsed for index: " + indexName);
            }

            // Load all existing entries (active + inactive) for this index
            Map<String, IndexMaster> existingBySymbol =
                    indexMasterRepository.findAllByIndexName(indexName).stream()
                            .collect(Collectors.toMap(IndexMaster::getSymbol, Function.identity()));

            Set<String> incomingSymbols = incoming.stream()
                    .map(IndexMaster::getSymbol)
                    .collect(Collectors.toSet());

            List<IndexMaster> toSave = new ArrayList<>();
            int added = 0, updated = 0;

            for (IndexMaster entry : incoming) {
                IndexMaster existing = existingBySymbol.get(entry.getSymbol());
                if (existing == null) {
                    toSave.add(entry);
                    added++;
                } else {
                    // Update metadata and ensure it's active
                    existing.setCompanyName(entry.getCompanyName());
                    existing.setSector(entry.getSector());
                    existing.setIsin(entry.getIsin());
                    existing.setIsActive(true);
                    toSave.add(existing);
                    updated++;
                }
            }

            // Deactivate symbols no longer in the index
            int deactivated = 0;
            for (IndexMaster existing : existingBySymbol.values()) {
                if (Boolean.TRUE.equals(existing.getIsActive())
                        && !incomingSymbols.contains(existing.getSymbol())) {
                    existing.setIsActive(false);
                    toSave.add(existing);
                    deactivated++;
                }
            }

            indexMasterRepository.saveAll(toSave);

            long timeTaken = System.currentTimeMillis() - startTime;
            log.info("✓ {} — added={}, updated={}, deactivated={}, {}ms",
                    indexName, added, updated, deactivated, timeTaken);

            return IndexSyncResult.builder()
                    .indexName(indexName)
                    .status("SUCCESS")
                    .addedCount(added)
                    .updatedCount(updated)
                    .deactivatedCount(deactivated)
                    .totalActiveCount(incoming.size())
                    .servedFromCache(downloadResult.isServedFromCache())
                    .timeTakenMs(timeTaken)
                    .build();

        } catch (Exception ex) {
            long timeTaken = System.currentTimeMillis() - startTime;
            log.warn("✗ {} — FAILED: {}", indexName, ex.getMessage());
            return IndexSyncResult.builder()
                    .indexName(indexName)
                    .status("FAILED")
                    .timeTakenMs(timeTaken)
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }

    // ── List synced indices with constituent counts ────────────────────────────
    public IndexListResponse getIndexList() {
        List<Object[]> summary = indexMasterRepository.findActiveIndexSummary();

        List<IndexInfo> syncedIndices = summary.stream()
                .map(row -> IndexInfo.builder()
                        .indexName((String) row[0])
                        .activeCount(((Long) row[1]).intValue())
                        .build())
                .collect(Collectors.toList());

        List<String> supported = NseIndexDownloader.getSupportedIndexNames();

        return IndexListResponse.builder()
                .syncedIndices(syncedIndices)
                .supportedIndices(supported)
                .totalSynced(syncedIndices.size())
                .totalSupported(supported.size())
                .build();
    }

    // ── Get active symbols for a given index ──────────────────────────────────
    public List<String> getActiveSymbols(String indexName) {
        return indexMasterRepository
                .findByIndexNameAndIsActiveTrue(indexName.toUpperCase())
                .stream()
                .map(IndexMaster::getSymbol)
                .collect(Collectors.toList());
    }
}
