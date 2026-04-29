package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexScreenerResponse {

    private String            timeframe;
    private String            period;
    private long              totalMatched;
    private int               page;
    private int               size;
    private List<IndexScreenerResult> results;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndexScreenerResult {
        private String     indexName;
        private LocalDate  candleDate;
        private LocalDate  candleEndDate;

        // OHLC
        private BigDecimal open;
        private BigDecimal close;
        private BigDecimal high;
        private BigDecimal low;

        // Computed
        private BigDecimal pctChange;   // ((close - open) / open) * 100

        // Trend + dates
        private String     trend;
        private LocalDate  highValueDate;
        private LocalDate  lowValueDate;

        // Volume extremes
        private Long       highVolQty;
        private LocalDate  highVolDate;
        private Long       lowVolQty;
        private LocalDate  lowVolDate;

        // Fundamentals (period average)
        private BigDecimal avgPe;
        private BigDecimal avgPb;
        private BigDecimal avgDivYield;
    }
}
