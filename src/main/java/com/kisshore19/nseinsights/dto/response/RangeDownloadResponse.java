package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RangeDownloadResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private int totalDatesRequested;
    private int successCount;
    private int failedCount;
    private int skippedCount;       // already existed and overwrite=false
    private int totalRecordsLoaded;
    private long totalTimeTakenMs;
    private List<DateDownloadResult> results;  // per-date breakdown
}