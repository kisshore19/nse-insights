package com.kisshore19.nseinsights.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Builder
public class IndexListResponse {

    /** Indices that have been synced and have active constituents. */
    private List<IndexInfo> syncedIndices;

    /** All indices this application supports downloading. */
    private List<String> supportedIndices;

    private int totalSynced;
    private int totalSupported;

    @Getter
    @Builder
    public static class IndexInfo {
        private String indexName;
        private int    activeCount;
    }
}
