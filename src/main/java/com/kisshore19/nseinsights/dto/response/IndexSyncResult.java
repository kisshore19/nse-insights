package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexSyncResult {

    private String  indexName;
    private String  status;          // SUCCESS | FAILED

    private int     addedCount;
    private int     updatedCount;
    private int     deactivatedCount;
    private int     totalActiveCount;

    private boolean servedFromCache;
    private Long    timeTakenMs;

    private String  errorMessage;
}
