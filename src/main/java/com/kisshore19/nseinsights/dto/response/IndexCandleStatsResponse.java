package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexCandleStatsResponse {

    private String     indexName;
    private String     timeframe;
    private String     periodKey;
    private LocalDate  candleDate;       // period start
    private LocalDate  candleEndDate;    // period end

    // OHLC
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal lastValue;
    private String     trend;            // UP | DOWN | SIDE
    private LocalDate  highValueDate;
    private LocalDate  lowValueDate;

    // Volume extremes
    private VolumePoint highestVolume;
    private VolumePoint lowestVolume;

    // Period-average fundamentals
    private BigDecimal avgPe;
    private BigDecimal avgPb;
    private BigDecimal avgDivYield;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VolumePoint {
        private LocalDate date;
        private Long      quantity;
    }
}
