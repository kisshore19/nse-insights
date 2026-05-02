package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexGroupResponse {

    private LocalDate tradeDate;
    private int totalIndices;
    private List<IndexGroup> indices;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndexGroup {
        private String indexName;
        private int stockCount;
        private int matchedCount;
        private BigDecimal avgPctChange;
        private BigDecimal maxPctChange;
        private BigDecimal minPctChange;
        private List<StockDto> stocks;
    }
}
