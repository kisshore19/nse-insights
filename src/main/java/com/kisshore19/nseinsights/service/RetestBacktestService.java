package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.RetestBacktestResponse;
import com.kisshore19.nseinsights.dto.response.RetestBacktestResponse.*;
import com.kisshore19.nseinsights.util.PeriodKeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;

/**
 * Backtest of the previous-quarter volume level retest, run on demand over nse_daily_price.
 *
 * Level  : high-low of the base quarter's highest-volume (or highest-delivery) day.
 * Setup  : in the next quarter the daily range first touches the level. The previous close
 *          decides the side: above the level = LONG (support), below = SHORT (resistance).
 * Entry  : LONG when a close is above the level high (SHORT: close below the level low),
 *          at that close, after the touch and before the quarter ends.
 * Stop   : LONG below the lowest low since the touch (or the level low if lower), SHORT mirrored.
 * Target : LONG the highest high of the lookback bars before the touch (SHORT the lowest low).
 * Exit   : first of target or stop (stop wins when both hit on one day), gaps fill at the open.
 */
@Service
@RequiredArgsConstructor
public class RetestBacktestService {

    private static final String TF = "QUARTER";

    private final JdbcTemplate jdbc;

    public record Params(String from, String to, String source, String side, boolean requireBreakout,
                         int lookback, double stopBufferPct, int maxHoldDays, double minRR,
                         String strategy, String targetMode, double targetR, int minDays, String signal,
                         String indexMode, String indexScope) {
        boolean useVol() { return !signal.equals("DELIVERY"); }
        boolean useDel() { return !signal.equals("VOLUME"); }
        boolean strict() { return strategy.equals("VOLUME_CANDLE"); }
    }

    /** What an index did in a quarter: which way its volume candle setup points and when it triggered (null = not yet). */
    private record IdxPat(boolean isLong, LocalDate trigger) {}

    /** Per stock list of its indices and, per index, the pattern found for each trade quarter. */
    private record IndexGate(Map<String, List<String>> indicesOf, Map<String, Map<String, IdxPat>> patterns, boolean triggered) {
        boolean allows(String sym, boolean isLong, String targetPeriod, LocalDate entryDate) {
            for (String idx : indicesOf.getOrDefault(sym, List.of())) {
                IdxPat pat = patterns.getOrDefault(idx, Map.of()).get(targetPeriod);
                if (pat == null || pat.isLong() != isLong) continue;
                if (!triggered || (pat.trigger() != null && !pat.trigger().isAfter(entryDate))) return true;
            }
            return false;
        }
    }

    private record Level(String base, String target, LocalDate baseEnd, LocalDate targetFrom, LocalDate targetTo,
                         double high, double low) {}

    private record Bar(LocalDate date, double open, double high, double low, double close, long vol, long del) {}

    private record Stat(String period, long volQty, double volHigh, double volLow,
                        long delQty, double delHigh, double delLow, boolean hasVol, boolean hasDel) {}

    public RetestBacktestResponse run(Params p) {
        validate(p);
        // the volume candle strategy is defined on volume and on a volume breakout of the base quarter
        // volume candle strategy: the signal decides which measure drives breakout, level and signal candle
        boolean vol = p.strict() ? p.signal().equals("VOLUME") || p.signal().equals("BOTH") : p.source().equals("VOL");
        boolean needBreakout = p.strict() || p.requireBreakout();

        // 1. levels: every base quarter in range paired with the quarter right after it
        Map<String, List<Level>> levelsBySymbol = new HashMap<>();
        Map<String, Map<String, Stat>> stats = loadStats();
        int levelCount = 0;
        LocalDate minFrom = null;
        for (Map.Entry<String, Map<String, Stat>> e : stats.entrySet()) {
            for (Stat s : e.getValue().values()) {
                if (s.period().compareTo(p.from()) < 0 || s.period().compareTo(p.to()) > 0) continue;
                if (vol ? !s.hasVol() : !s.hasDel()) continue;
                if (needBreakout) {
                    Stat prev = e.getValue().get(prevQuarter(s.period()));
                    boolean checkVol = p.strict() ? p.useVol() : vol, checkDel = p.strict() ? p.useDel() : !vol;
                    if (checkVol && (prev == null || s.volQty() <= prev.volQty())) continue;
                    if (checkDel && (prev == null || s.delQty() <= prev.delQty())) continue;
                }
                String target = nextQuarter(s.period());
                LocalDate tf = PeriodKeyUtils.periodStart(TF, target);
                LocalDate tt = PeriodKeyUtils.periodEnd(TF, target);
                levelsBySymbol.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Level(
                        s.period(), target, PeriodKeyUtils.periodEnd(TF, s.period()), tf, tt,
                        vol ? s.volHigh() : s.delHigh(), vol ? s.volLow() : s.delLow()));
                levelCount++;
                if (minFrom == null || tf.isBefore(minFrom)) minFrom = tf;
            }
        }

        Acc acc = new Acc();
        acc.levels = levelCount;
        if (p.strict() && !p.indexMode().equals("NONE")) acc.gate = buildIndexGate(p);
        if (minFrom == null) return acc.toResponse(p);

        // 2. stream daily bars symbol by symbol, run each level of that symbol
        LocalDate barsFrom = minFrom.minusDays(Math.max(30, (long) p.lookback() * 2));
        String sql = "SELECT symbol, trade_date, open_price, high_price, low_price, close_price, traded_quantity, COALESCE(delivery_qty, 0) "
                + "FROM nse_daily_price WHERE series = 'EQ' AND trade_date >= ? ORDER BY symbol, trade_date";
        String[] cur = {null};
        List<Bar> bars = new ArrayList<>();
        Runnable flush = () -> {
            List<Level> lv = cur[0] == null ? null : levelsBySymbol.get(cur[0]);
            if (lv != null) for (Level l : lv) simulate(cur[0], bars, l, p, acc);
            bars.clear();
        };
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);       // stream rows instead of buffering about 3M of them
            ps.setObject(1, barsFrom);
            return ps;
        }, rs -> {
            String sym = rs.getString(1);
            if (!sym.equals(cur[0])) { flush.run(); cur[0] = sym; }
            bars.add(new Bar(rs.getDate(2).toLocalDate(), rs.getDouble(3), rs.getDouble(4),
                    rs.getDouble(5), rs.getDouble(6), rs.getLong(7), rs.getLong(8)));
        });
        flush.run();
        return acc.toResponse(p);
    }

    // ── simulation for one symbol and one level ────────────────────────────────────────────

    private void simulate(String sym, List<Bar> bars, Level lv, Params p, Acc acc) {
        if (p.strict()) { simulateVolumeCandle(sym, bars, lv, p, acc); return; }
        int n = bars.size();
        int i0 = 0;
        while (i0 < n && bars.get(i0).date().isBefore(lv.targetFrom())) i0++;
        if (i0 == 0 || i0 >= n) return;                       // no bars in the target quarter, or no previous close

        // first touch inside the target quarter
        int t = -1;
        for (int i = i0; i < n && !bars.get(i).date().isAfter(lv.targetTo()); i++) {
            Bar b = bars.get(i);
            if (b.low() <= lv.high() && b.high() >= lv.low()) { t = i; break; }
        }
        if (t < 0) return;
        acc.touched++;

        double prevClose = bars.get(t - 1).close();
        boolean isLong;
        if (prevClose > lv.high()) isLong = true;
        else if (prevClose < lv.low()) isLong = false;
        else { acc.inside++; return; }
        if (isLong) acc.longSetups++; else acc.shortSetups++;
        if (isLong && p.side().equals("SHORT") || !isLong && p.side().equals("LONG")) return;

        // previous made high (long target) or low (short target) before the touch
        int ws = Math.max(0, t - p.lookback());
        double prevExtreme = isLong ? -Double.MAX_VALUE : Double.MAX_VALUE;
        for (int i = ws; i < t; i++) {
            prevExtreme = isLong ? Math.max(prevExtreme, bars.get(i).high()) : Math.min(prevExtreme, bars.get(i).low());
        }

        // wait for the trigger close, tracking the extreme made since the touch
        double extreme = isLong ? lv.low() : lv.high();       // lowest low (long) / highest high (short)
        int e = -1;
        for (int i = t; i < n && !bars.get(i).date().isAfter(lv.targetTo()); i++) {
            Bar b = bars.get(i);
            extreme = isLong ? Math.min(extreme, b.low()) : Math.max(extreme, b.high());
            if (isLong ? b.close() > lv.high() : b.close() < lv.low()) { e = i; break; }
            if (isLong ? b.close() < lv.low() : b.close() > lv.high()) { acc.invalidated++; return; }
        }
        if (e < 0) { acc.noTrigger++; return; }

        double entry = bars.get(e).close();
        double stop = isLong ? extreme * (1 - p.stopBufferPct() / 100) : extreme * (1 + p.stopBufferPct() / 100);
        double risk = isLong ? entry - stop : stop - entry;
        double reward = isLong ? prevExtreme - entry : entry - prevExtreme;
        if (risk <= 0 || reward <= 0 || reward / risk < p.minRR()) { acc.noRoom++; return; }
        acc.triggered++;

        finish(sym, bars, lv, p, acc, isLong, bars.get(t).date(), e, e, false, entry, stop, prevExtreme, risk, reward);
    }

    /**
     * Volume candle strategy. Base quarter (Q2) peak volume beat the quarter before it, and Q2 closed
     * above (long) or below (short) the peak volume candle. In the next quarter (Q3) the signal candle is
     * a day whose range touches that level while its volume is the highest of Q3 so far. It stays the
     * signal only while nothing trades more volume before the trigger. Long trigger is a CLOSE above the
     * signal candle high, then entry at that high on the first later day trading back to it, stop at the
     * signal candle low. Short is the mirror image (close below the low, entry at the low, stop at the high).
     */
    private void simulateVolumeCandle(String sym, List<Bar> bars, Level lv, Params p, Acc acc) {
        int n = bars.size();
        int b = -1;
        for (int i = 0; i < n && !bars.get(i).date().isAfter(lv.baseEnd()); i++) b = i;
        if (b < 0) return;
        double baseClose = bars.get(b).close();
        boolean isLong;
        if (baseClose > lv.high()) isLong = true;
        else if (baseClose < lv.low()) isLong = false;
        else { acc.inside++; return; }                         // Q2 closed inside the level: no side
        if (isLong) acc.longSetups++; else acc.shortSetups++;
        if (isLong && p.side().equals("SHORT") || !isLong && p.side().equals("LONG")) return;

        int i0 = 0;
        while (i0 < n && bars.get(i0).date().isBefore(lv.targetFrom())) i0++;
        if (i0 >= n) return;

        Sig sig = findSignal(bars, lv, p, acc, isLong, i0);
        if (sig == null) return;
        int c = sig.c(), trig = sig.trig();
        Bar cb = bars.get(c);
        double entry = isLong ? cb.high() : cb.low();           // price to be bought / sold
        double far = isLong ? cb.low() : cb.high();             // stop side of the signal candle
        {
            // phase 2: entry on the first later day that trades back to the signal candle edge
            int e = -1;
            boolean failed = false;
            for (int k = trig + 1; k < n && !bars.get(k).date().isAfter(lv.targetTo()); k++) {
                Bar kb = bars.get(k);
                if (kb.low() <= entry && kb.high() >= entry) { e = k; break; }
                if (isLong ? kb.low() < far : kb.high() > far) { failed = true; break; }
            }
            if (failed) { acc.invalidated++; return; }
            if (e < 0) { acc.noTrigger++; return; }
            if (acc.gate != null && !acc.gate.allows(sym, isLong, lv.target(), bars.get(e).date())) { acc.indexBlocked++; return; }

            double stop = isLong ? far * (1 - p.stopBufferPct() / 100) : far * (1 + p.stopBufferPct() / 100);
            double risk = isLong ? entry - stop : stop - entry;
            double target;
            if (p.targetMode().equals("R")) {
                target = isLong ? entry + p.targetR() * risk : entry - p.targetR() * risk;
            } else {
                target = isLong ? -Double.MAX_VALUE : Double.MAX_VALUE;
                for (int i = Math.max(0, c - p.lookback()); i < c; i++) {
                    target = isLong ? Math.max(target, bars.get(i).high()) : Math.min(target, bars.get(i).low());
                }
            }
            double reward = isLong ? target - entry : entry - target;
            if (risk <= 0 || reward <= 0 || reward / risk < p.minRR()) { acc.noRoom++; return; }
            acc.triggered++;
            finish(sym, bars, lv, p, acc, isLong, cb.date(), trig, e, true, entry, stop, target, risk, reward);
        }
    }

    private record Sig(int c, int trig) {}

    /**
     * Finds the first signal candle in the trade quarter that is followed by a close beyond it, or null.
     * Counts touched, invalidated and noTrigger on acc.
     */
    private Sig findSignal(List<Bar> bars, Level lv, Params p, Acc acc, boolean isLong, int i0) {
        int n = bars.size();
        long[] priorMax = new long[n];                         // highest Q3 volume before each day
        long[] priorDel = new long[n];                         // highest Q3 delivery before each day
        for (int i = i0 + 1; i < n; i++) {
            priorMax[i] = Math.max(priorMax[i - 1], bars.get(i - 1).vol());
            priorDel[i] = Math.max(priorDel[i - 1], bars.get(i - 1).del());
        }
        boolean found = false;
        // a signal candle needs a few days of quarter history, otherwise day one is trivially the peak
        for (int c = i0 + p.minDays(); c < n && !bars.get(c).date().isAfter(lv.targetTo()); c++) {
            Bar cb = bars.get(c);
            boolean peak = (!p.useVol() || cb.vol() > 0 && cb.vol() >= priorMax[c])
                    && (!p.useDel() || cb.del() > 0 && cb.del() >= priorDel[c]);
            if (!(peak && cb.low() <= lv.high() && cb.high() >= lv.low())) continue;
            if (!found) { acc.touched++; found = true; }

            double entry = isLong ? cb.high() : cb.low();
            double far = isLong ? cb.low() : cb.high();
            // wait for a close beyond the signal candle; more volume or losing the far side cancels it
            int trig = -1;
            boolean cancelled = false;
            for (int j = c + 1; j < n && !bars.get(j).date().isAfter(lv.targetTo()); j++) {
                Bar jb = bars.get(j);
                boolean moreVolume = p.useVol() && jb.vol() > cb.vol() || p.useDel() && jb.del() > cb.del();
                boolean lost = isLong ? jb.low() < far : jb.high() > far;
                if (moreVolume || lost) { acc.invalidated++; cancelled = true; c = j - 1; break; }
                if (isLong ? jb.close() > entry : jb.close() < entry) { trig = j; break; }
            }
            if (cancelled) continue;                           // the cancelling day is checked as a new signal
            if (trig < 0) { acc.noTrigger++; return null; }    // quarter ended with no close beyond the candle
            return new Sig(c, trig);
        }
        return null;
    }

    // ── index confirmation ─────────────────────────────────────────────────────────────────

    /**
     * Runs the same volume candle pattern on every index (volume only, index data has no delivery) and
     * links stocks to the indices they belong to. Index levels are built straight from daily index bars.
     */
    private IndexGate buildIndexGate(Params p) {
        Params ip = new Params(p.from(), p.to(), p.source(), p.side(), true, p.lookback(), p.stopBufferPct(),
                p.maxHoldDays(), p.minRR(), p.strategy(), p.targetMode(), p.targetR(), p.minDays(), "VOLUME",
                "NONE", p.indexScope());

        // index_key -> daily close index name; sector indices are mapped in index_sector_map, others by name
        Map<String, String> keyToName = new HashMap<>();
        Set<String> sectorKeys = new HashSet<>();
        jdbc.query("SELECT daily_close_name, index_key FROM index_sector_map", rs -> {
            keyToName.put(rs.getString(2), rs.getString(1)); sectorKeys.add(rs.getString(2));
        });
        List<String> names = jdbc.queryForList("SELECT DISTINCT index_name FROM index_daily_close", String.class);
        for (String nm : names) keyToName.putIfAbsent(nm.replaceAll("[^A-Za-z0-9]", "").toUpperCase(), nm);

        Map<String, List<String>> indicesOf = new HashMap<>();
        jdbc.query("SELECT symbol, index_name FROM index_master WHERE is_active = 1", rs -> {
            String key = rs.getString(2);
            boolean sector = sectorKeys.contains(key);
            if (p.indexScope().equals("SECTOR") && !sector || p.indexScope().equals("BROAD") && sector) return;
            String nm = keyToName.get(key);
            if (nm != null) indicesOf.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(nm);
        });
        Set<String> wanted = new HashSet<>();
        indicesOf.values().forEach(wanted::addAll);

        Map<String, Map<String, IdxPat>> patterns = new HashMap<>();
        String[] cur = {null};
        List<Bar> bars = new ArrayList<>();
        Runnable flush = () -> {
            if (cur[0] != null && wanted.contains(cur[0])) patterns.put(cur[0], indexPatterns(bars, ip));
            bars.clear();
        };
        jdbc.query("SELECT index_name, trade_date, open_value, high_value, low_value, close_value, volume "
                + "FROM index_daily_close WHERE volume IS NOT NULL AND volume > 0 AND high_value IS NOT NULL "
                + "AND low_value IS NOT NULL ORDER BY index_name, trade_date", rs -> {
            String nm = rs.getString(1);
            if (!nm.equals(cur[0])) { flush.run(); cur[0] = nm; }
            bars.add(new Bar(rs.getDate(2).toLocalDate(), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getLong(7), 0));
        });
        flush.run();
        return new IndexGate(indicesOf, patterns, p.indexMode().equals("TRIGGERED"));
    }

    private static String quarterKey(LocalDate d) {
        return d.getYear() + "-Q" + ((d.getMonthValue() - 1) / 3 + 1);
    }

    /** Pattern per trade quarter for one index: base peak volume candle, breakout vs the quarter before, side, trigger date. */
    private Map<String, IdxPat> indexPatterns(List<Bar> bars, Params ip) {
        // peak volume day per quarter, earliest day on ties
        Map<String, Integer> peak = new TreeMap<>();
        for (int i = 0; i < bars.size(); i++) {
            String q = quarterKey(bars.get(i).date());
            Integer cur = peak.get(q);
            if (cur == null || bars.get(i).vol() > bars.get(cur).vol()) peak.put(q, i);
        }
        Map<String, IdxPat> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : peak.entrySet()) {
            String base = e.getKey();
            if (base.compareTo(ip.from()) < 0 || base.compareTo(ip.to()) > 0) continue;
            Integer prev = peak.get(prevQuarter(base));
            Bar pk = bars.get(e.getValue());
            if (prev == null || pk.vol() <= bars.get(prev).vol()) continue;           // no volume breakout in the base quarter
            String target = nextQuarter(base);
            Level lv = new Level(base, target, PeriodKeyUtils.periodEnd(TF, base),
                    PeriodKeyUtils.periodStart(TF, target), PeriodKeyUtils.periodEnd(TF, target), pk.high(), pk.low());
            int b = -1;
            for (int i = 0; i < bars.size() && !bars.get(i).date().isAfter(lv.baseEnd()); i++) b = i;
            if (b < 0) continue;
            double close = bars.get(b).close();
            boolean isLong;
            if (close > lv.high()) isLong = true; else if (close < lv.low()) isLong = false; else continue;
            int i0 = 0;
            while (i0 < bars.size() && bars.get(i0).date().isBefore(lv.targetFrom())) i0++;
            if (i0 >= bars.size()) { out.put(target, new IdxPat(isLong, null)); continue; }
            Sig sig = findSignal(bars, lv, ip, new Acc(), isLong, i0);
            out.put(target, new IdxPat(isLong, sig == null ? null : bars.get(sig.trig()).date()));
        }
        return out;
    }

    /**
     * Walks forward from the entry bar and records the trade. Stop wins when both levels hit on one day,
     * gaps fill at the open. On the entry bar itself (entered intraday) only the intraday levels apply.
     */
    private void finish(String sym, List<Bar> bars, Level lv, Params p, Acc acc, boolean isLong, LocalDate touch,
                        int trig, int e, boolean intradayEntry, double entry, double stop, double target,
                        double risk, double reward) {
        int n = bars.size();
        int x = n - 1;
        double exit = bars.get(n - 1).close();
        String why = "OPEN";
        for (int k = intradayEntry ? e : e + 1; k < n; k++) {
            Bar b = bars.get(k);
            boolean gapOk = !(intradayEntry && k == e);
            if (isLong) {
                if (gapOk && b.open() <= stop)   { x = k; exit = b.open(); why = "STOP"; break; }
                if (b.low() <= stop)             { x = k; exit = stop;     why = "STOP"; break; }
                if (gapOk && b.open() >= target) { x = k; exit = b.open(); why = "TARGET"; break; }
                if (b.high() >= target)          { x = k; exit = target;   why = "TARGET"; break; }
            } else {
                if (gapOk && b.open() >= stop)   { x = k; exit = b.open(); why = "STOP"; break; }
                if (b.high() >= stop)            { x = k; exit = stop;     why = "STOP"; break; }
                if (gapOk && b.open() <= target) { x = k; exit = b.open(); why = "TARGET"; break; }
                if (b.low() <= target)           { x = k; exit = target;   why = "TARGET"; break; }
            }
            if (p.maxHoldDays() > 0 && k - e >= p.maxHoldDays()) { x = k; exit = b.close(); why = "TIME"; break; }
        }
        double pnl = isLong ? exit - entry : entry - exit;
        acc.trades.add(new Trade(sym, isLong ? "LONG" : "SHORT", lv.base(), lv.target(),
                r2(lv.high()), r2(lv.low()), touch, bars.get(trig).date(), bars.get(e).date(),
                r2(entry), r2(stop), r2(target), r2(reward / risk),
                bars.get(x).date(), r2(exit), why, r2(pnl / entry * 100), r2(pnl / risk), x - e));
    }

    // ── data ───────────────────────────────────────────────────────────────────────────────

    private Map<String, Map<String, Stat>> loadStats() {
        Map<String, Map<String, Stat>> out = new HashMap<>();
        jdbc.query("SELECT symbol, period_key, high_vol_qty, high_vol_day_high, high_vol_day_low, "
                + "high_deliv_qty, high_deliv_day_high, high_deliv_day_low FROM candle_stats WHERE timeframe = ?",
                rs -> {
                    Double vh = rs.getObject(4) == null ? null : rs.getDouble(4);
                    Double vl = rs.getObject(5) == null ? null : rs.getDouble(5);
                    Double dh = rs.getObject(7) == null ? null : rs.getDouble(7);
                    Double dl = rs.getObject(8) == null ? null : rs.getDouble(8);
                    out.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), new Stat(
                            rs.getString(2), rs.getLong(3), vh == null ? 0 : vh, vl == null ? 0 : vl,
                            rs.getLong(6), dh == null ? 0 : dh, dl == null ? 0 : dl,
                            vh != null && vl != null, dh != null && dl != null));
                }, TF);
        return out;
    }

    private static void validate(Params p) {
        PeriodKeyUtils.validatePeriodKey(TF, p.from());
        PeriodKeyUtils.validatePeriodKey(TF, p.to());
        if (p.to().compareTo(p.from()) < 0) throw new IllegalArgumentException("to must not be before from.");
        if (!List.of("VOL", "DELIV").contains(p.source())) throw new IllegalArgumentException("source must be VOL or DELIV.");
        if (!List.of("BOTH", "LONG", "SHORT").contains(p.side())) throw new IllegalArgumentException("side must be BOTH, LONG or SHORT.");
        if (p.lookback() < 5 || p.lookback() > 250) throw new IllegalArgumentException("lookback must be between 5 and 250.");
        if (p.stopBufferPct() < 0 || p.stopBufferPct() > 20) throw new IllegalArgumentException("stopBufferPct must be between 0 and 20.");
        if (p.maxHoldDays() < 0) throw new IllegalArgumentException("maxHoldDays must not be negative.");
        if (p.minRR() < 0) throw new IllegalArgumentException("minRR must not be negative.");
        if (!List.of("LEVEL_RETEST", "VOLUME_CANDLE").contains(p.strategy())) throw new IllegalArgumentException("strategy must be LEVEL_RETEST or VOLUME_CANDLE.");
        if (!List.of("NONE", "SETUP", "TRIGGERED").contains(p.indexMode())) throw new IllegalArgumentException("indexMode must be NONE, SETUP or TRIGGERED.");
        if (!List.of("ALL", "BROAD", "SECTOR").contains(p.indexScope())) throw new IllegalArgumentException("indexScope must be ALL, BROAD or SECTOR.");
        if (!List.of("VOLUME", "BOTH", "DELIVERY").contains(p.signal())) throw new IllegalArgumentException("signal must be VOLUME, BOTH or DELIVERY.");
        if (!List.of("EXTREME", "R").contains(p.targetMode())) throw new IllegalArgumentException("targetMode must be EXTREME or R.");
        if (p.minDays() < 0 || p.minDays() > 60) throw new IllegalArgumentException("minDays must be between 0 and 60.");
        if (p.targetR() <= 0 || p.targetR() > 20) throw new IllegalArgumentException("targetR must be between 0 and 20.");
    }

    private static String nextQuarter(String key) {
        int y = Integer.parseInt(key.substring(0, 4)), q = key.charAt(6) - '0';
        return q == 4 ? (y + 1) + "-Q1" : y + "-Q" + (q + 1);
    }

    private static String prevQuarter(String key) {
        int y = Integer.parseInt(key.substring(0, 4)), q = key.charAt(6) - '0';
        return q == 1 ? (y - 1) + "-Q4" : y + "-Q" + (q - 1);
    }

    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }

    // ── accumulation and statistics ────────────────────────────────────────────────────────

    private static class Acc {
        int levels, touched, inside, longSetups, shortSetups, noTrigger, invalidated, noRoom, triggered, indexBlocked;
        IndexGate gate;
        final List<Trade> trades = new ArrayList<>();

        RetestBacktestResponse toResponse(Params p) {
            trades.sort(Comparator.comparing(Trade::entryDate).thenComparing(Trade::symbol));
            Map<String, List<Trade>> per = new TreeMap<>();
            for (Trade t : trades) per.computeIfAbsent(t.targetPeriod(), k -> new ArrayList<>()).add(t);
            List<PeriodSummary> byPeriod = new ArrayList<>();
            per.forEach((k, v) -> byPeriod.add(new PeriodSummary(k, summarize(v))));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("from", p.from()); params.put("to", p.to()); params.put("source", p.source());
            params.put("side", p.side()); params.put("requireBreakout", p.requireBreakout());
            params.put("lookback", p.lookback()); params.put("stopBufferPct", p.stopBufferPct());
            params.put("maxHoldDays", p.maxHoldDays()); params.put("minRR", p.minRR());
            params.put("strategy", p.strategy()); params.put("targetMode", p.targetMode()); params.put("targetR", p.targetR()); params.put("minDays", p.minDays()); params.put("signal", p.signal()); params.put("indexMode", p.indexMode()); params.put("indexScope", p.indexScope());
            return new RetestBacktestResponse(params,
                    new Funnel(levels, touched, inside, longSetups, shortSetups, noTrigger, invalidated, noRoom, triggered, indexBlocked),
                    summarize(trades),
                    summarize(trades.stream().filter(t -> t.side().equals("LONG")).toList()),
                    summarize(trades.stream().filter(t -> t.side().equals("SHORT")).toList()),
                    byPeriod, trades);
        }

        static Summary summarize(List<Trade> all) {
            List<Trade> closed = all.stream().filter(t -> !t.exitReason().equals("OPEN"))
                    .sorted(Comparator.comparing(Trade::exitDate)).toList();
            int open = all.size() - closed.size();
            int wins = 0, losses = 0;
            double sumR = 0, winR = 0, lossR = 0, ret = 0, hold = 0, peak = 0, cum = 0, dd = 0;
            for (Trade t : closed) {
                if (t.rMultiple() > 0) { wins++; winR += t.rMultiple(); } else { losses++; lossR += t.rMultiple(); }
                sumR += t.rMultiple(); ret += t.returnPct(); hold += t.holdDays();
                cum += t.rMultiple(); peak = Math.max(peak, cum); dd = Math.max(dd, peak - cum);
            }
            int c = closed.size();
            return new Summary(c, wins, losses, open,
                    c == 0 ? 0 : r2(wins * 100.0 / c), c == 0 ? 0 : r2(sumR / c), r2(sumR),
                    wins == 0 ? 0 : r2(winR / wins), losses == 0 ? 0 : r2(lossR / losses),
                    c == 0 ? 0 : r2(ret / c), lossR == 0 ? (winR > 0 ? 999 : 0) : r2(winR / -lossR),
                    c == 0 ? 0 : r2(hold / c), r2(dd));
        }
    }
}
