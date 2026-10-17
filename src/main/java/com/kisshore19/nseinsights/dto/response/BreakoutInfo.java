package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Breakout of a period's high volume/delivery versus the previous period's.
 * breakout = quantity > previousQuantity (null for the first period — nothing to compare).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BreakoutInfo {
    private String     periodKey;         // only set in the history summary lists
    private Boolean    breakout;
    private LocalDate  date;              // date the period's high quantity occurred (the breakout day)
    private Long       quantity;          // this period's high quantity
    private Long       previousQuantity;  // previous period's high quantity
    private BigDecimal changePct;         // (quantity - previous) / previous * 100
}
