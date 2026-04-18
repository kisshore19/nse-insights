package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "candle_stats",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_stats_symbol_timeframe_period",
           columnNames = {"symbol", "timeframe", "period_key"}
       ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandleStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    // Human-readable period identifier — unique key per symbol+timeframe
    // YEAR    → "2024"
    // QUARTER → "2024-Q1"
    // MONTH   → "2024-01"
    // WEEK    → "2024-W03"
    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;

    // Period start — first trading day of the period
    @Column(name = "candle_date", nullable = false)
    private LocalDate candleDate;

    // Period end — last trading day of the period
    @Column(name = "candle_end_date", nullable = false)
    private LocalDate candleEndDate;

    // ── OHLC ──────────────────────────────────────────────────────────────────
    @Column(name = "open_price", precision = 12, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 12, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 12, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "last_price", precision = 12, scale = 2)
    private BigDecimal lastPrice;

    // UP = low formed before high | DOWN = high formed before low | SIDE = same day
    @Column(name = "trend", length = 5)
    private String trend;

    // Date when the period HIGH price was first reached
    @Column(name = "high_price_date")
    private LocalDate highPriceDate;

    // Date when the period LOW price was first reached
    @Column(name = "low_price_date")
    private LocalDate lowPriceDate;

    // ── Volume extremes ───────────────────────────────────────────────────────
    @Column(name = "high_vol_qty")
    private Long highVolQty;

    @Column(name = "high_vol_date")
    private LocalDate highVolDate;

    @Column(name = "low_vol_qty")
    private Long lowVolQty;

    @Column(name = "low_vol_date")
    private LocalDate lowVolDate;

    // ── Delivery extremes ─────────────────────────────────────────────────────
    @Column(name = "high_deliv_qty")
    private Long highDelivQty;

    @Column(name = "high_deliv_date")
    private LocalDate highDelivDate;

    @Column(name = "low_deliv_qty")
    private Long lowDelivQty;

    @Column(name = "low_deliv_date")
    private LocalDate lowDelivDate;

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