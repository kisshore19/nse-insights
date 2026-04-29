package com.kisshore19.nseinsights.dto.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndexSyncRequest {

    /**
     * Specific indices to sync. If null or empty, all supported indices are synced.
     * Example: ["NIFTY50", "NIFTY100"]
     */
    private List<String> indices;

    /**
     * If true, deletes the local cached file and forces a fresh download from NSE.
     */
    private boolean forceRefresh = false;
}
