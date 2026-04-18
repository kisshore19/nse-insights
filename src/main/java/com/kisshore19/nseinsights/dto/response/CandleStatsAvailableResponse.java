package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleStatsAvailableResponse {

    private String            timeframe;
    private int               totalPeriods;

    // Earliest and latest period keys available
    private String            from;
    private String            to;

    private List<PeriodEntry> periods;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PeriodEntry {
        private String    periodKey;      // "2024-W03" / "2024-01" / "2024-Q1" / "2024"
        private LocalDate candleDate;     // first trading day of period
        private LocalDate candleEndDate;  // last  trading day of period
    }
}