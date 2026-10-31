package com.kisshore19.nseinsights.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Result of the previous-quarter volume level retest backtest. Plain records, nothing is stored.
 */
public record RetestBacktestResponse(
        Map<String, Object> params,
        Funnel funnel,
        Summary all,
        Summary longs,
        Summary shorts,
        List<PeriodSummary> byPeriod,
        List<Trade> trades) {

    public record Funnel(int levels, int touched, int inside, int longSetups, int shortSetups,
                         int noTrigger, int invalidated, int noRoom, int triggered, int indexBlocked) {}

    /** Statistics over CLOSED trades only; open trades are counted separately. */
    public record Summary(int trades, int wins, int losses, int open, double winRate,
                          double avgR, double totalR, double avgWinR, double avgLossR,
                          double avgReturnPct, double profitFactor, double avgHoldDays,
                          double maxDrawdownR) {}

    public record PeriodSummary(String targetPeriod, Summary summary) {}

    public record Trade(String symbol, String side, String basePeriod, String targetPeriod,
                        double levelHigh, double levelLow, LocalDate touchDate, LocalDate triggerDate, LocalDate entryDate,
                        double entry, double stop, double target, double rr,
                        LocalDate exitDate, double exitPrice, String exitReason,
                        double returnPct, double rMultiple, int holdDays) {}
}
