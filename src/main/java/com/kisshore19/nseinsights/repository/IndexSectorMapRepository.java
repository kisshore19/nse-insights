package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.entity.IndexSectorMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexSectorMapRepository extends JpaRepository<IndexSectorMap, String> {

    Optional<IndexSectorMap> findByIndexKey(String indexKey);

    List<IndexSectorMap> findByIndexKeyIn(List<String> indexKeys);
}
