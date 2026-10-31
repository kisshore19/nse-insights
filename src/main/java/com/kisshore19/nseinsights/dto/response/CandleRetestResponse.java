package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleRetestResponse {

    private String    timeframe;
    private String    basePeriod;     // period whose breakout-day ranges are the levels
    private String    targetPeriod;   // period whose daily prices are checked against them
    private LocalDate targetFrom;
    private LocalDate targetTo;
    private List<Entry> results;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Entry {
        private String    symbol;
        private LocalDate volTouchDate;     // first day the price range touched the volume-day range
        private Long      volTouchDays;
        private LocalDate delivTouchDate;   // first day the price range touched the delivery-day range
        private Long      delivTouchDays;

        // Close of the trading day before the first touch: above the level = came down into it
        // (support), below = came up into it (resistance).
        private BigDecimal volApproachClose;
        private BigDecimal delivApproachClose;

        // First day after the target period's highest-volume day that closed outside the level.
        private LocalDate  volAfterDate;
        private BigDecimal volAfterClose;
        private LocalDate  delivAfterDate;
        private BigDecimal delivAfterClose;
    }
}
