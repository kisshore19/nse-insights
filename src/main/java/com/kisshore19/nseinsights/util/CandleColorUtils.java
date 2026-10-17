package com.kisshore19.nseinsights.util;

import java.math.BigDecimal;

/** Candle colour from open vs close: GREEN (close > open), RED (close < open), DOJI (equal). */
public final class CandleColorUtils {

    public static final String GREEN = "GREEN";
    public static final String RED   = "RED";
    public static final String DOJI  = "DOJI";

    private CandleColorUtils() {}

    /** Returns null when either price is missing. */
    public static String of(BigDecimal open, BigDecimal close) {
        if (open == null || close == null) return null;
        int cmp = close.compareTo(open);
        return cmp > 0 ? GREEN : cmp < 0 ? RED : DOJI;
    }
}
