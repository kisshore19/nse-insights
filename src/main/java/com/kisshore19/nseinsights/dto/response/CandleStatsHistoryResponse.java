package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/** All stored periods of one timeframe for a single stock, oldest first. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleStatsHistoryResponse {

    private String      symbol;
    private String      timeframe;
    private int         totalPeriods;
    private int         volumeBreakoutCount;
    private List<BreakoutInfo> volumeBreakouts;     // periods whose high volume beat the previous period
    private int         deliveryBreakoutCount;
    private List<BreakoutInfo> deliveryBreakouts;
    private List<CandleStatsResponse> periods;
}
