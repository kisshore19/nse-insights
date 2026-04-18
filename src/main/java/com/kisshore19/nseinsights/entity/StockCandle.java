package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_candle",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_candle_symbol_timeframe_date",
                columnNames = {"symbol", "timeframe", "candle_date"}
        ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    // Start date of the period
    @Column(name = "candle_date", nullable = false)
    private LocalDate candleDate;

    // End date of the period (last trading day)
    @Column(name = "candle_end_date", nullable = false)
    private LocalDate candleEndDate;

    // ── OHLC ──────────────────────────────────────────────────────────────────
    // Open  = first trading day's open price
    @Column(name = "open_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal openPrice;

    // High  = max high across all days in period
    @Column(name = "high_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal highPrice;

    // Low   = min low across all days in period
    @Column(name = "low_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lowPrice;

    // Close = last trading day's LAST_PRICE (actual last traded price)
    @Column(name = "close_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal closePrice;

    // ── Candle Direction ──────────────────────────────────────────────────────
    // true = GREEN (close > open), false = RED (close <= open)
    @Column(name = "is_green", nullable = false)
    private Boolean isGreen;

    // ── Trend (intra-period price path) ───────────────────────────────────────
    // UP   = low formed on an earlier date than high (price went up)
    // DOWN = high formed on an earlier date than low (price came down)
    // SIDE = high and low formed on the same day (single trading day or indeterminate)
    @Column(name = "trend", nullable = false, length = 5)
    private String trend;

    // Date when the period HIGH was reached
    @Column(name = "high_date")
    private LocalDate highDate;

    // Date when the period LOW was reached
    @Column(name = "low_date")
    private LocalDate lowDate;

    // ── Volume ────────────────────────────────────────────────────────────────
    // Total traded quantity across all days in period
    @Column(name = "total_volume", nullable = false)
    private Long totalVolume;

    // Date with the highest single-day traded volume
    @Column(name = "max_volume_date")
    private LocalDate maxVolumeDate;

    // Volume on the max volume day
    @Column(name = "max_volume_day_qty")
    private Long maxVolumeDayQty;

    // ── Delivery ──────────────────────────────────────────────────────────────
    // Total delivery quantity across all days in period
    @Column(name = "total_delivery")
    private Long totalDelivery;

    // Date with the highest single-day delivery quantity
    @Column(name = "max_delivery_date")
    private LocalDate maxDeliveryDate;

    // Delivery quantity on the max delivery day
    @Column(name = "max_delivery_day_qty")
    private Long maxDeliveryDayQty;

    // Number of trading days in this candle period
    @Column(name = "trading_days")
    private Integer tradingDays;

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

    // ── Timeframe constants ───────────────────────────────────────────────────
    public static final String TF_DAY     = "DAY";
    public static final String TF_WEEK    = "WEEK";
    public static final String TF_MONTH   = "MONTH";
    public static final String TF_QUARTER = "QUARTER";
    public static final String TF_YEAR    = "YEAR";

    // ── Trend constants ───────────────────────────────────────────────────────
    public static final String TREND_UP   = "UP";
    public static final String TREND_DOWN = "DOWN";
    public static final String TREND_SIDE = "SIDE";
}