package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for individual stock data in Data Explorer responses.
 * Contains OHLCV and delivery information.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StockDto {

    private Long id;
    private LocalDate tradeDate;
    private String symbol;
    private String series;
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
