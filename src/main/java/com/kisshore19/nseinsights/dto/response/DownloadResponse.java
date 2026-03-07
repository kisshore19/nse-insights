package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DownloadResponse {
    private LocalDate tradeDate;
    private int recordsLoaded;
    private String bhavatopyFile;
    private String mtoFile;
    private long timeTakenMs;
    private Integer downloadLogId;
}