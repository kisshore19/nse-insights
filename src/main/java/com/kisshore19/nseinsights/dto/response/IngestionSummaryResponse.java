package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestionSummaryResponse {
    private long totalDatesLoaded;
    private LocalDate latestTradeDate;
    private LocalDate oldestTradeDate;
    private long totalRecords;
    private LocalDateTime lastDownloadedAt;
    private long failedDownloads;
}
