package com.kisshore19.nseinsights.util;

import com.kisshore19.nseinsights.dto.response.BreakoutInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class BreakoutUtils {

    private BreakoutUtils() {}

    /** Null when the period has no high quantity recorded. */
    public static BreakoutInfo compare(Long current, LocalDate currentDate, Long previous) {
        if (current == null) return null;
        Boolean breakout = null;
        BigDecimal pct   = null;
        if (previous != null) {
            breakout = current > previous;
            if (previous != 0) {
                pct = BigDecimal.valueOf(current - previous)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
            }
        }
        return BreakoutInfo.builder()
                .breakout(breakout).date(currentDate)
                .quantity(current).previousQuantity(previous).changePct(pct)
                .build();
    }

    public static BreakoutInfo withPeriod(BreakoutInfo info, String periodKey) {
        return BreakoutInfo.builder()
                .periodKey(periodKey).breakout(info.getBreakout()).date(info.getDate())
                .quantity(info.getQuantity()).previousQuantity(info.getPreviousQuantity())
                .changePct(info.getChangePct())
                .build();
    }
}
