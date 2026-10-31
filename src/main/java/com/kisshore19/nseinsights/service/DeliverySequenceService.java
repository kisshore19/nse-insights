package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.DeliverySequenceResponse;
import com.kisshore19.nseinsights.dto.response.DeliverySequenceResponse.Day;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists every completed session of a symbol in a date range, flagging delivery rises and cool-off days.
 *
 * RISE     : delivery qty above the previous session's.
 * COOL_OFF : delivery qty below the previous session's, directly after a RISE or another COOL_OFF day.
 * Each cool-off period (consecutive cool-off days) is read from two candles: the candle just before it and its first
 * cool-off day. Their structure (HH, LL, HV, LV) and their combined high / low, compared with the previous period's.
 * A rise that follows a cool-off day starts the next sequence.
 */
@Service
@RequiredArgsConstructor
public class DeliverySequenceService {

    private final NseDailyPriceRepository priceRepository;

    public record Params(String symbol, LocalDate from, LocalDate to) {}

    public DeliverySequenceResponse run(Params p) {
        if (p.symbol() == null || p.symbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (p.from() != null && p.to() != null && p.from().isAfter(p.to())) throw new IllegalArgumentException("from must not be after to");
        String symbol = p.symbol().trim().toUpperCase();

        List<NseDailyPrice> all = priceRepository.findBySymbolOrderByTradeDateAsc(symbol);
        boolean hasEq = all.stream().anyMatch(r -> "EQ".equals(r.getSeries()));
        LocalDate today = LocalDate.now();
        List<NseDailyPrice> rows = new ArrayList<>();
        for (NseDailyPrice r : all) {
            if (hasEq && !"EQ".equals(r.getSeries())) continue;
            if (!r.getTradeDate().isBefore(today)) continue;   // completed sessions only
            if (p.to() != null && r.getTradeDate().isAfter(p.to())) continue;
            if (r.getDeliveryQty() == null || r.getTradedQuantity() == null || r.getTradedQuantity() <= 0) continue;
            rows.add(r);
        }

        List<Day> days = new ArrayList<>();
        int sequence = 0, base = -1;
        String prevPhase = "";
        List<Integer> rowIdx = new ArrayList<>();   // index into rows of each entry in days
        for (int i = 0; i < rows.size(); i++) {
            NseDailyPrice r = rows.get(i);
            if (p.from() != null && r.getTradeDate().isBefore(p.from())) {
                // sessions before `from` only set the state going into the range
                if (i > 0) {
                    long prev = rows.get(i - 1).getDeliveryQty();
                    if (r.getDeliveryQty() > prev) { if (!prevPhase.equals("RISE")) sequence++; prevPhase = "RISE"; }
                    else if (r.getDeliveryQty() < prev && !prevPhase.isEmpty()) prevPhase = "COOL_OFF";
                    else prevPhase = "";
                }
                continue;
            }
            if (base < 0) base = sequence;   // number sequences from the first day in range
            Long prev = i > 0 ? rows.get(i - 1).getDeliveryQty() : null;
            String phase = "";
            if (prev != null) {
                if (r.getDeliveryQty() > prev) { phase = "RISE"; if (!prevPhase.equals("RISE")) sequence++; }
                else if (r.getDeliveryQty() < prev && !prevPhase.isEmpty()) phase = "COOL_OFF";
            }
            prevPhase = phase;
            days.add(new Day(r.getTradeDate(), r.getOpenPrice().doubleValue(), r.getHighPrice().doubleValue(),
                    r.getLowPrice().doubleValue(), r.getClosePrice().doubleValue(), r.getTradedQuantity(),
                    r.getDeliveryQty(), Math.max(0, sequence - base), phase, phase.equals("COOL_OFF"),
                    0, null, null, "", ""));
            rowIdx.add(i);
        }

        // one structure per cool-off period (consecutive cool-off days), compared with the previous period's
        Double prevHigh = null, prevLow = null;
        int coolNo = 0;
        for (int d = 0; d < days.size(); ) {
            if (!days.get(d).coolOff()) { d++; continue; }
            int e = d;
            while (e + 1 < days.size() && days.get(e + 1).coolOff()) e++;
            // the period is two candles: the one before the first cool-off day and the first cool-off day
            NseDailyPrice before = rows.get(rowIdx.get(d) - 1);
            double bh = before.getHighPrice().doubleValue(), bl = before.getLowPrice().doubleValue();
            double dayHigh = days.get(d).high(), dayLow = days.get(d).low();   // first cool-off day only
            double high = Math.max(bh, dayHigh), low = Math.min(bl, dayLow);
            coolNo++;
            // own structure: first cool-off day against the candle before it
            boolean hh = dayHigh > bh, lh = dayHigh < bh, hl = dayLow > bl, ll = dayLow < bl;
            String own = summary(hh, lh, hl, ll);
            String struc = own;
            String toPrev = "-";
            if (prevHigh != null) {
                String sum = summary(high > prevHigh, high < prevHigh, low > prevLow, low < prevLow);
                toPrev = sum;
            }
            for (int k = d; k <= e; k++) {
                Day x = days.get(k);
                boolean f = k == d;
                days.set(k, new Day(x.date(), x.open(), x.high(), x.low(), x.close(), x.tradedQty(), x.deliveryQty(),
                        x.sequence(), x.phase(), true, coolNo, f ? high : null, f ? low : null,
                        f ? struc : "", f ? toPrev : ""));
            }
            prevHigh = high;
            prevLow = low;
            d = e + 1;
        }
        return new DeliverySequenceResponse(symbol, days.isEmpty() ? p.from() : days.get(0).date(),
                days.isEmpty() ? p.to() : days.get(days.size() - 1).date(), days);
    }

    /**
     * HH = higher high and higher low, LL = lower high and lower low, HV = higher high with lower low (range expands),
     * LV = lower high with higher low (range contracts), "=" when none of those apply.
     */
    private static String summary(boolean highUp, boolean highDown, boolean lowUp, boolean lowDown) {
        if (highUp && lowDown) return "HV";
        if (highDown && lowUp) return "LV";
        if (highUp && !lowDown) return "HH";
        if (lowDown && !highUp) return "LL";
        return "=";
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
