package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "index_candle_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idx_stats_name_tf_period",
                columnNames = {"index_name", "timeframe", "period_key"}
        ),
        indexes = {
                @Index(name = "idx_idx_stats_name_tf", columnList = "index_name, timeframe"),
                @Index(name = "idx_idx_stats_tf",      columnList = "timeframe")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IndexCandleStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "index_name",     nullable = false, length = 100)
    private String indexName;

    @Column(name = "timeframe",      nullable = false, length = 10)
    private String timeframe;

    // YEAR→"2024"  QUARTER→"2024-Q1"  MONTH→"2024-01"  WEEK→"2024-W03"
    @Column(name = "period_key",     nullable = false, length = 10)
    private String periodKey;

    @Column(name = "candle_date",     nullable = false)
    private LocalDate candleDate;       // first trading day of period

    @Column(name = "candle_end_date", nullable = false)
    private LocalDate candleEndDate;    // last  trading day of period

    // ── OHLC ──────────────────────────────────────────────────────────────────
    @Column(name = "open_value",  precision = 12, scale = 2)
    private BigDecimal openValue;

    @Column(name = "high_value",  precision = 12, scale = 2)
    private BigDecimal highValue;

    @Column(name = "low_value",   precision = 12, scale = 2)
    private BigDecimal lowValue;

    @Column(name = "last__value",  precision = 12, scale = 2)
    private BigDecimal lastValue;       // close value on last trading day

    // UP = low formed before high | DOWN = high formed before low | SIDE = same day
    @Column(name = "trend",       length = 5)
    private String trend;

    @Column(name = "high_value_date")
    private LocalDate highValueDate;    // date when period high was first reached

    @Column(name = "low_value_date")
    private LocalDate lowValueDate;     // date when period low  was first reached

    // ── Volume extremes ───────────────────────────────────────────────────────
    @Column(name = "high_vol_qty")
    private Long highVolQty;

    @Column(name = "high_vol_date")
    private LocalDate highVolDate;

    @Column(name = "low_vol_qty")
    private Long lowVolQty;

    @Column(name = "low_vol_date")
    private LocalDate lowVolDate;

    // ── Period-average fundamentals (P/E, P/B, Div Yield) ────────────────────
    @Column(name = "avg_pe",        precision = 8, scale = 2)
    private BigDecimal avgPe;

    @Column(name = "avg_pb",        precision = 8, scale = 2)
    private BigDecimal avgPb;

    @Column(name = "avg_div_yield", precision = 7, scale = 2)
    private BigDecimal avgDivYield;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
