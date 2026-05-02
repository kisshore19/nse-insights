package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "index_sector_map",
       indexes = @Index(name = "idx_sector_map_index_key", columnList = "index_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexSectorMap {

    /** Exact index_name as stored in index_daily_close (from NSE CSV col 1). e.g. "Nifty IT" */
    @Id
    @Column(name = "daily_close_name", nullable = false, length = 100)
    private String dailyCloseName;

    /** Short code used in index_master.index_name and NseIndexDownloader. e.g. "NIFTYIT" */
    @Column(name = "index_key", nullable = false, length = 50)
    private String indexKey;
}
