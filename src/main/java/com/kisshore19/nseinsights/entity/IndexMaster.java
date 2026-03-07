package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "index_master",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_index_symbol",
           columnNames = {"index_name", "symbol"}
       ),
       indexes = {
           @Index(name = "idx_idx_master_symbol", columnList = "symbol"),
           @Index(name = "idx_idx_master_index", columnList = "index_name"),
           @Index(name = "idx_idx_master_sector", columnList = "sector")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "index_name", nullable = false, length = 50)
    private String indexName;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "isin", length = 20)
    private String isin;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "added_date")
    private LocalDate addedDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
