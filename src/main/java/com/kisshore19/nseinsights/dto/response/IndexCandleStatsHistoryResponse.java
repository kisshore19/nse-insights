package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/** All stored periods of one timeframe for a single index, oldest first. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexCandleStatsHistoryResponse {

    private String      indexName;
    private String      timeframe;
    private int         totalPeriods;
    private int         volumeBreakoutCount;
    private List<BreakoutInfo> volumeBreakouts;     // periods whose high volume beat the previous period
    private List<IndexCandleStatsResponse> periods;
}
