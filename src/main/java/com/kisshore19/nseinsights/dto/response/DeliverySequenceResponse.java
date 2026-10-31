package com.kisshore19.nseinsights.dto.response;

import java.time.LocalDate;
import java.util.List;

/** One row per session for a symbol, with the delivery rise / cool-off flags. */
public record DeliverySequenceResponse(String symbol, LocalDate from, LocalDate to, List<Day> days) {

    /**
     * @param sequence       number of the rise run this day belongs to (0 = before the first rise)
     * @param phase          RISE (delivery above the previous session's), COOL_OFF (below it, after a rise) or empty
     * Cool-off columns are filled on the first day of each cool-off period only (coolNo is on every cool-off day).
     * @param coolNo         cool-off period number (0 = not a cool-off day)
     * @param coolHigh       highest high of the period, including the candle before it
     * @param coolLow        lowest low of the period, including the candle before it
     * @param coolOffStruc   the period's own high / low against the candle before it, HH, LL, HV or LV
     * @param coolToCool     coolHigh / coolLow against the previous cool-off period's, HH, LL, HV or LV; "-" for the first period
     */
    public record Day(LocalDate date, double open, double high, double low, double close,
                      long tradedQty, long deliveryQty,
                      int sequence, String phase, boolean coolOff,
                      int coolNo, Double coolHigh, Double coolLow, String coolOffStruc, String coolToCool) {}
}
