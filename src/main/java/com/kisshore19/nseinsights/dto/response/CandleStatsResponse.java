package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleStatsResponse {

    private String     symbol;
    private String     timeframe;
    private String     periodKey;       // "2024" / "2024-Q1" / "2024-01" / "2024-W03"
    private LocalDate  candleDate;      // period start
    private LocalDate  candleEndDate;   // period end

    // OHLC
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal lastPrice;
    private String     trend;           // UP | DOWN | SIDE
    private LocalDate  highPriceDate;   // date when period high was first reached
    private LocalDate  lowPriceDate;    // date when period low  was first reached

    // Volume extremes
    private VolumePoint highestVolume;
    private VolumePoint lowestVolume;

    // Delivery extremes
    private VolumePoint highestDelivery;
    private VolumePoint lowestDelivery;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VolumePoint {
        private LocalDate date;
        private Long      quantity;
    }
}