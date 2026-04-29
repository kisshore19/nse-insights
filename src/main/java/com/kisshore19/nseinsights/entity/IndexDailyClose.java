package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "index_daily_close",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idx_close_name_date",
                columnNames = {"index_name", "trade_date"}
        ),
        indexes = {
                @Index(name = "idx_close_trade_date",  columnList = "trade_date DESC"),
                @Index(name = "idx_close_index_name",  columnList = "index_name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexDailyClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_name",  nullable = false, length = 100)
    private String indexName;

    @Column(name = "trade_date",  nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_value",  precision = 12, scale = 2)
    private BigDecimal openValue;

    @Column(name = "high_value",  precision = 12, scale = 2)
    private BigDecimal highValue;

    @Column(name = "low_value",   precision = 12, scale = 2)
    private BigDecimal lowValue;

    @Column(name = "close_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal closeValue;

    @Column(name = "points_change", precision = 12, scale = 2)
    private BigDecimal pointsChange;

    @Column(name = "pct_change",    precision = 7,  scale = 2)
    private BigDecimal pctChange;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "turnover",   precision = 18, scale = 2)
    private BigDecimal turnover;

    @Column(name = "pe",         precision = 8,  scale = 2)
    private BigDecimal pe;

    @Column(name = "pb",         precision = 8,  scale = 2)
    private BigDecimal pb;

    @Column(name = "div_yield",  precision = 7,  scale = 2)
    private BigDecimal divYield;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
