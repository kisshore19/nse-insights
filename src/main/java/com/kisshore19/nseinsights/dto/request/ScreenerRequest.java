package com.kisshore19.nseinsights.dto.request;

import lombok.*;
import java.math.BigDecimal;

/**
 * All screener filter params — every field is optional except timeframe + period.
 * Passed directly from controller query params to service.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScreenerRequest {

    // ── Scope (required) ──────────────────────────────────────────────────────
    private String     timeframe;       // YEAR | QUARTER | MONTH | WEEK
    private String     period;          // "2024" / "2024-Q1" / "2024-01" / "2024-W03"

    // ── Price filters ─────────────────────────────────────────────────────────
    private BigDecimal minClose;        // last_price >= minClose
    private BigDecimal maxClose;        // last_price <= maxClose

    // ── % change intra-period: ((close - open) / open) * 100 ─────────────────
    private BigDecimal minPctChange;    // computed in spec, not a stored column
    private BigDecimal maxPctChange;

    // ── Trend ─────────────────────────────────────────────────────────────────
    private String     trend;           // UP | DOWN | SIDE  (null = all)

    // ── Volume filter (value in crores — multiplied by 1_00_00_000 internally) ─
    private BigDecimal minHighVol;      // high_vol_qty >= minHighVol * 1Cr

    // ── Delivery filter (value in crores) ─────────────────────────────────────
    private BigDecimal minHighDeliv;    // high_deliv_qty >= minHighDeliv * 1Cr

    // ── Delivery % of volume ──────────────────────────────────────────────────
    // (high_deliv_qty / high_vol_qty) * 100 >= minDelivPct
    private BigDecimal minDelivPct;

    // ── High/Low price date position within the period ────────────────────────
    // EARLY = date falls in first half of period
    // LATE  = date falls in second half of period
    // null  = no filter
    private String     highDatePos;     // EARLY | LATE
    private String     lowDatePos;      // EARLY | LATE

    // ── Sort + pagination ─────────────────────────────────────────────────────
    private String     sortBy  = "PCT_CHANGE";   // CLOSE | PCT_CHANGE | HIGH_VOL | HIGH_DELIV | DELIV_PCT
    private String     sortDir = "DESC";          // ASC | DESC
    private int        page    = 0;
    private int        size    = 50;              // max 200
}