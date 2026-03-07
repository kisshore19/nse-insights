package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nse_daily_price",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_price_date_symbol",
           columnNames = {"trade_date", "symbol", "series"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NseDailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "series", nullable = false, length = 5)
    private String series;

    @Column(name = "open_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "prev_close", precision = 12, scale = 2)
    private BigDecimal prevClose;

    @Column(name = "pct_change", precision = 7, scale = 2)
    private BigDecimal pctChange;

    @Column(name = "traded_quantity", nullable = false)
    private Long tradedQuantity;

    @Column(name = "turnover", precision = 18, scale = 2)
    private BigDecimal turnover;

    @Column(name = "delivery_qty")
    private Long deliveryQty;

    @Column(name = "delivery_pct", precision = 7, scale = 2)
    private BigDecimal deliveryPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
