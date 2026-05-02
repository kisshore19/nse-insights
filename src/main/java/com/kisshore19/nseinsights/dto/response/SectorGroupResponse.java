package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectorGroupResponse {

    private LocalDate tradeDate;
    private int totalSectors;
    private List<SectorGroup> sectors;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorGroup {
        private String sectorName;
        private int stockCount;
        private int matchedCount;
        private BigDecimal avgPctChange;
        private BigDecimal maxPctChange;
        private BigDecimal minPctChange;
        // Sectoral index stats from index_daily_close (null if no sectoral index found)
        private String sectorIndexKey;    // e.g. "NIFTYIT"
        private String sectorIndexName;   // e.g. "Nifty IT" (as in index_daily_close)
        private BigDecimal indexClose;
        private BigDecimal indexPctChange;
        private BigDecimal indexPe;
        private BigDecimal indexPb;
        private BigDecimal indexDivYield;
        private List<SectorStockDto> stocks;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorStockDto {
        private String symbol;
        private List<String> indices;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private BigDecimal prevClose;
        private BigDecimal pctChange;
        private Long tradedQuantity;
        private BigDecimal turnover;
        private Long deliveryQty;
        private BigDecimal deliveryPct;
    }
}
