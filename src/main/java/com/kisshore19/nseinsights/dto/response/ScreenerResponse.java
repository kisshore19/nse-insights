package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenerResponse {

    private String            timeframe;
    private String            period;
    private long              totalMatched;   // total rows matching filters
    private int               page;
    private int               size;
    private List<ScreenerResult> results;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScreenerResult {
        private String     symbol;
        private LocalDate  candleDate;       // period start
        private LocalDate  candleEndDate;    // period end

        // OHLC
        private BigDecimal open;
        private BigDecimal close;
        private BigDecimal high;
        private BigDecimal low;

        // Computed fields
        private BigDecimal pctChange;        // ((close - open) / open) * 100
        private BigDecimal delivPct;         // (highDelivQty / highVolQty) * 100

        // Trend + dates
        private String     trend;
        private String     candleColor;   // GREEN | RED | DOJI (close vs open)
        private LocalDate  highPriceDate;
        private LocalDate  lowPriceDate;

        // Volume extremes
        private Long       highVolQty;
        private LocalDate  highVolDate;
        private BigDecimal highVolDayHigh;
        private BigDecimal highVolDayLow;
        private Long       lowVolQty;
        private LocalDate  lowVolDate;
        private BigDecimal lowVolDayHigh;
        private BigDecimal lowVolDayLow;

        // Delivery extremes
        private Long       highDelivQty;
        private LocalDate  highDelivDate;
        private BigDecimal highDelivDayHigh;
        private BigDecimal highDelivDayLow;
        private Long       lowDelivQty;
        private LocalDate  lowDelivDate;
        private BigDecimal lowDelivDayHigh;
        private BigDecimal lowDelivDayLow;
    }
}