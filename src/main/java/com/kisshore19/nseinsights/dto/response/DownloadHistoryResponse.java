package com.kisshore19.nseinsights.dto.response;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DownloadHistoryResponse {
    private List<DownloadHistoryItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
