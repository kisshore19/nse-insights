package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.DeliverySequenceResponse;
import com.kisshore19.nseinsights.dto.response.DeliverySequenceResponse.Day;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

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

        List<DeliverySequenceResponse.Period> periods = new ArrayList<>();
        Map<LocalDate, String[]> flags = periodFlags(rows, p.from(), periods);
        String[] none = {"", "", "", ""};
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
                    0, null, null, "", "",
                    flags.getOrDefault(r.getTradeDate(), none)[0], flags.getOrDefault(r.getTradeDate(), none)[1],
                    flags.getOrDefault(r.getTradeDate(), none)[2], flags.getOrDefault(r.getTradeDate(), none)[3]));
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
                        f ? struc : "", f ? toPrev : "",
                        x.highVol(), x.highDel(), x.volBreakout(), x.delBreakout()));
            }
            prevHigh = high;
            prevLow = low;
            d = e + 1;
        }
        return new DeliverySequenceResponse(symbol, days.isEmpty() ? p.from() : days.get(0).date(),
                days.isEmpty() ? p.to() : days.get(days.size() - 1).date(), days, periods);
    }

    private static final String[] TF = {"W", "M", "Q", "Y"};

    private static String periodKey(int tf, LocalDate d) {
        return switch (tf) {
            case 0 -> d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case 1 -> d.getYear() + "-" + String.format("%02d", d.getMonthValue());
            case 2 -> d.getYear() + "-Q" + d.get(IsoFields.QUARTER_OF_YEAR);
            default -> String.valueOf(d.getYear());
        };
    }

    /**
     * Per date, for each of week / month / quarter / year: [highest volume, highest delivery, volume breakout,
     * delivery breakout] as comma separated period codes.
     * Highest = the top day among the sessions inside the selected range. Breakout = the first day of a period whose
     * value is above the previous period's highest, the previous period taken from all loaded data (not just the range).
     */
    private static Map<LocalDate, String[]> periodFlags(List<NseDailyPrice> rows, LocalDate from,
                                                         List<DeliverySequenceResponse.Period> periods) {
        Map<LocalDate, String[]> out = new HashMap<>();
        for (int tf = 0; tf < TF.length; tf++) {
            Map<String, long[]> full = new LinkedHashMap<>();   // period -> {max volume, max delivery}, all data
            for (NseDailyPrice r : rows) {
                long[] m = full.computeIfAbsent(periodKey(tf, r.getTradeDate()), k -> new long[2]);
                m[0] = Math.max(m[0], r.getTradedQuantity());
                m[1] = Math.max(m[1], r.getDeliveryQty());
            }
            Map<String, String> prev = new HashMap<>();
            String last = null;
            for (String k : full.keySet()) { if (last != null) prev.put(k, last); last = k; }

            Map<String, LocalDate> topVol = new HashMap<>(), topDel = new HashMap<>();   // inside the range
            Map<String, Long> topVolQty = new HashMap<>(), topDelQty = new HashMap<>();
            Set<String> volDone = new HashSet<>(), delDone = new HashSet<>();
            for (NseDailyPrice r : rows) {
                LocalDate d = r.getTradeDate();
                String k = periodKey(tf, d);
                String pk = prev.get(k);
                if (pk != null && r.getTradedQuantity() > full.get(pk)[0] && volDone.add(k) && inRange(d, from)) add(out, d, 2, TF[tf]);
                if (pk != null && r.getDeliveryQty() > full.get(pk)[1] && delDone.add(k) && inRange(d, from)) add(out, d, 3, TF[tf]);
                if (!inRange(d, from)) continue;
                if (r.getTradedQuantity() > topVolQty.getOrDefault(k, -1L)) { topVolQty.put(k, r.getTradedQuantity()); topVol.put(k, d); }
                if (r.getDeliveryQty() > topDelQty.getOrDefault(k, -1L)) { topDelQty.put(k, r.getDeliveryQty()); topDel.put(k, d); }
            }
            for (LocalDate d : topVol.values()) add(out, d, 0, TF[tf]);
            for (LocalDate d : topDel.values()) add(out, d, 1, TF[tf]);

            // one summary row per period that has sessions inside the range
            Map<String, LocalDate[]> span = new LinkedHashMap<>();   // period -> {first, last} day in range
            for (NseDailyPrice r : rows) {
                if (!inRange(r.getTradeDate(), from)) continue;
                LocalDate[] sp = span.computeIfAbsent(periodKey(tf, r.getTradeDate()), k -> new LocalDate[]{r.getTradeDate(), r.getTradeDate()});
                sp[1] = r.getTradeDate();
            }
            for (Map.Entry<String, LocalDate[]> en : span.entrySet()) {
                String k = en.getKey(), pk = prev.get(k);
                long mv = topVolQty.get(k), md = topDelQty.get(k);
                Long pv = pk == null ? null : full.get(pk)[0], pd = pk == null ? null : full.get(pk)[1];
                periods.add(new DeliverySequenceResponse.Period(TF[tf], k, en.getValue()[0], en.getValue()[1],
                        mv, topVol.get(k), pv, change(mv, pv), pv != null && mv > pv,
                        md, topDel.get(k), pd, change(md, pd), pd != null && md > pd));
            }
        }
        return out;
    }

    private static Double change(long cur, Long prev) {
        return prev == null || prev == 0 ? null : round((cur - prev) * 100.0 / prev);
    }

    private static boolean inRange(LocalDate d, LocalDate from) { return from == null || !d.isBefore(from); }

    private static void add(Map<LocalDate, String[]> out, LocalDate d, int slot, String code) {
        String[] f = out.computeIfAbsent(d, k -> new String[]{"", "", "", ""});
        f[slot] = f[slot].isEmpty() ? code : f[slot] + "," + code;
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
