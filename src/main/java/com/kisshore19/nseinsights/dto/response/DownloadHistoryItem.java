package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownloadHistoryItem {
    private Integer id;
    private LocalDate tradeDate;
    private String status;
    private Integer recordCount;
    private String fileName;
    private String errorMessage;
    private LocalDateTime downloadedAt;
    private LocalDateTime completedAt;
    private Long timeTakenMs;
}
