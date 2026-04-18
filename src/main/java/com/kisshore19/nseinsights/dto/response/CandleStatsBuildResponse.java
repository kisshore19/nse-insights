package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandleStatsBuildResponse {

    private String       timeframe;

    // All period keys that were in the requested range
    private List<String> periodsRequested;

    // Periods that already had stats — not touched
    private List<String> periodsSkipped;

    // Periods that were newly computed and inserted
    private List<String> periodsInserted;

    // Total rows saved across all inserted periods
    private int          recordsInserted;
}