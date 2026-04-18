package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleResponse {

    private String symbol;
    private String timeframe;
    private int totalCandles;
    private List<CandleData> candles;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CandleData {

        // Period
        private LocalDate candleDate;       // period start
        private LocalDate candleEndDate;    // period end (last trading day)
        private Integer tradingDays;        // number of trading days in period

        // OHLC
        private BigDecimal open;            // first day open
        private BigDecimal high;            // period high
        private BigDecimal low;             // period low
        private BigDecimal close;           // last day LAST_PRICE

        // Direction
        private Boolean isGreen;            // true = close > open
        private String color;               // "GREEN" or "RED" (convenience)

        // Trend (price path within period)
        private String trend;               // "UP" / "DOWN" / "SIDE"
        private LocalDate highDate;         // date when period high was formed
        private LocalDate lowDate;          // date when period low was formed

        // Volume
        private Long totalVolume;           // sum of all traded qty
        private LocalDate maxVolumeDate;    // day with highest volume
        private Long maxVolumeDayQty;       // volume on that day

        // Delivery
        private Long totalDelivery;         // sum of all delivery qty
        private LocalDate maxDeliveryDate;  // day with highest delivery
        private Long maxDeliveryDayQty;     // delivery qty on that day
    }
}