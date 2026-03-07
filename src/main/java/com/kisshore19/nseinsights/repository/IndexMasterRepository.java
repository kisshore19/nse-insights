package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.IndexMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndexMasterRepository extends JpaRepository<IndexMaster, Integer> {

    // Get all distinct sectors for active indices
    @Query("SELECT DISTINCT im.sector FROM IndexMaster im WHERE im.isActive = true ORDER BY im.sector ASC")
    List<String> findAllDistinctActiveSectors();

    // Find all symbols for a given index
    List<IndexMaster> findByIndexNameAndIsActiveTrue(String indexName);

    // Find all active indices by sector
    List<IndexMaster> findByIsActiveTrueAndSector(String sector);
}
