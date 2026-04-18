package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleBuildResponse {
    private String timeframe;
    private String symbol;          // null if built for all symbols
    private int totalCandlesBuilt;
    private long timeTakenMs;
    private String message;
    private Map<String, Integer> candleCountByTimeframe;  // for build-all
}