package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DateDownloadResult {
    private LocalDate tradeDate;
    private String status;          // SUCCESS, FAILED, SKIPPED, ALREADY_EXISTS
    private Integer recordsLoaded;
    private String message;
    private Long timeTakenMs;
}