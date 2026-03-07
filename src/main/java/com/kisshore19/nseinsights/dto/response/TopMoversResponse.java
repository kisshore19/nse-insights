package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Response for top gainers and losers APIs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopMoversResponse {

    private List<StockDto> stocks;
    private LocalDate tradeDate;
    private String category;  // "GAINERS" or "LOSERS"
    private int count;
}
