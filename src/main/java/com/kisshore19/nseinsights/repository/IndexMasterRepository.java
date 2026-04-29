package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.IndexMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndexMasterRepository extends JpaRepository<IndexMaster, Integer> {

    @Query("SELECT DISTINCT im.sector FROM IndexMaster im WHERE im.isActive = true ORDER BY im.sector ASC")
    List<String> findAllDistinctActiveSectors();

    // Active constituents for an index (used for stock filtering)
    List<IndexMaster> findByIndexNameAndIsActiveTrue(String indexName);

    // ALL records for an index (active + inactive) — used during sync/upsert
    List<IndexMaster> findAllByIndexName(String indexName);

    // Active indices by sector
    List<IndexMaster> findByIsActiveTrueAndSector(String sector);

    // Distinct active index names (for index list)
    @Query("SELECT DISTINCT im.indexName FROM IndexMaster im WHERE im.isActive = true ORDER BY im.indexName ASC")
    List<String> findDistinctActiveIndexNames();

    // Per-index active count — returns [indexName, count] pairs
    @Query("SELECT im.indexName, COUNT(im) FROM IndexMaster im WHERE im.isActive = true GROUP BY im.indexName ORDER BY im.indexName ASC")
    List<Object[]> findActiveIndexSummary();

    // Symbol lookup across all active indices (used for enriching stock detail)
    List<IndexMaster> findBySymbolAndIsActiveTrue(String symbol);
}
