package com.kisshore19.nseinsights.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Builder
public class IndexSyncResponse {

    private int               syncedIndices;
    private int               totalAdded;
    private int               totalUpdated;
    private int               totalDeactivated;
    private long              timeTakenMs;
    private List<IndexSyncResult> results;
}
