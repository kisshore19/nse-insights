package com.kisshore19.nseinsights.backtesting;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.*;

/**
 * SetupScanner.java  --  Multi-Timeframe Quarterly Retest Setup
 * ================================================================
 *
 * TIMEFRAME HIERARCHY:
 *   YEARLY    trend  ->  QUARTERLY retest  ->  daily entry
 *   QUARTERLY trend  ->  MONTHLY   retest  ->  daily entry
 *   MONTHLY   trend  ->  WEEKLY    retest  ->  daily entry
 *
 * SETUP LOGIC (same at every level):
 *   SELL:
 *     T1 (trend candle) = DOWNTREND (high before low, close < open)
 *     T2 (retest candle) HIGH touches T1 open within tolerance
 *     T2 retest candle monthly/weekly CLOSE is below T1 open (gate)
 *     Daily bar within T2 that CLOSES below T1 open = ENTRY
 *
 *   BUY:
 *     T1 (trend candle) = UPTREND (low before high, close > open)
 *     T2 (retest candle) LOW touches T1 open within tolerance
 *     T2 retest candle monthly/weekly CLOSE is above T1 open (gate)
 *     Daily bar within T2 that CLOSES above T1 open = ENTRY
 *
 * USAGE:
 *   javac SetupScanner.java
 *
 *   All timeframes, all symbols:
 *     java SetupScanner both ./bhavcopy_folder ./output
 *
 *   Specific timeframe:
 *     java SetupScanner both ./bhavcopy_folder ./output ALL quarterly
 *     java SetupScanner both ./bhavcopy_folder ./output ALL monthly
 *     java SetupScanner both ./bhavcopy_folder ./output ALL yearly
 *
 *   Specific symbol:
 *     java SetupScanner both ./bhavcopy_folder ./output RELIANCE quarterly
 *
 * INPUT: NSE bhavcopy CSV files (one per day)
 *   SYMBOL,SERIES,DATE1,PREV_CLOSE,OPEN_PRICE,HIGH_PRICE,
 *   LOW_PRICE,LAST_PRICE,CLOSE_PRICE,AVG_PRICE,TTL_TRD_QNTY,
 *   TURNOVER_LACS,NO_OF_TRADES,DELIV_QTY,DELIV_PER
 *   (col 7 = LAST_PRICE used as closing price)
 * ================================================================
 */
public class SetupScanner {

    // ── CONFIG ───────────────────────────────────────────────────
    static final double RETEST_TOLERANCE_PCT = 0.0;
    static final double MIN_RR               = 1.5;
    static final DateTimeFormatter DATE_FMT  =
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);

    // ── TIMEFRAME ENUM ───────────────────────────────────────────
    enum TF { YEARLY, QUARTERLY, MONTHLY }

    // ── AGGREGATED CANDLE (works for weekly, monthly, quarterly, yearly) ──
    static class Candle {
        String    symbol;
        String    periodKey;   // e.g. "2024-Q3" / "2024-07" / "2024-W32" / "2024"
        String    tfName;      // YEARLY / QUARTERLY / MONTHLY / WEEKLY
        LocalDate start, end;
        double    open, high, low, close;
        LocalDate highDate, lowDate;
        long      totalVol, totalDeliv;
        double    avgDelivPer;
        int       bars;
        String    trend;            // UP / DOWN / FLAT
        boolean   closeAboveOpen;
        double    bodyPct;

        double fullRange() { return high - low; }
        double bodySize()  { return Math.abs(close - open); }
    }

    // ── SIGNAL ───────────────────────────────────────────────────
    static class Signal {
        String    symbol;
        TF        timeframe;
        String    tfLabel;        // "YEARLY->QUARTERLY" etc
        String    t1Period;       // trend candle period
        String    t2Period;       // retest candle period
        String    side;           // BUY / SELL

        double    t1Open;         // = entry level = options strike
        double    t1High;         // stop for SELL
        double    t1Low;          // stop for BUY, target for SELL
        double    t2RetestDistPct;

        LocalDate retestDate;     // exact daily bar that touched T1 open
        double    retestPrice;
        LocalDate entryDate;      // exact daily bar that confirmed close
        double    entryClose;
        boolean   sameCandle;

        String    t1Trend, t2Trend;
        double    t2BodyPct;
        double    riskAmt, rewardAmt, rr;

        boolean   backtestComplete;
        boolean   targetHit, stopHit;
        double    t3Low, t3High;
        String    outcome;

        String stopLevel()   { return side.equals("SELL") ? fmt(t1High) : fmt(t1Low); }
        String targetLevel() { return side.equals("SELL") ? fmt(t1Low)  : fmt(t1High); }
        String optionsAction() {
            return side.equals("SELL")
                    ? "Sell CALLS at " + fmt(t1Open)
                    : "Sell PUTS  at " + fmt(t1Open);
        }
    }

    // ── DAILY BAR ────────────────────────────────────────────────
    static class Bar {
        String symbol; LocalDate date;
        double open, high, low, close;
        long volume, delivQty; double delivPer;
    }

    // ── MAIN ─────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        //        if (args.length < 3) { printUsage(); return; }

        String mode      = "both";
        String bhavDir    = "E:\\StockResearch\\Data\\NseData\\Bhav-Data";
        String outputDir  = "E:\\StockResearch\\nse-insights\\output";
        String filterSym =  "ALL";
        String filterTF  = "all";

        if (filterSym.equals("ALL")) filterSym = "";

        printBanner();
        System.out.println("Mode         : " + mode.toUpperCase());
        System.out.println("Bhavcopy dir : " + bhavDir);
        System.out.println("Output dir   : " + outputDir);
        System.out.println("Symbol filter: " + (filterSym.isEmpty() ? "ALL" : filterSym));
        System.out.println("TF filter    : " + filterTF.toUpperCase());
        System.out.println("Tolerance    : " + RETEST_TOLERANCE_PCT + "%");
        System.out.println();

        // Load all daily bars
        Map<String, List<Bar>> dailyBySym = loadBhavcopies(bhavDir, filterSym);
        if (dailyBySym.isEmpty()) { System.out.println("No data loaded."); return; }
        System.out.printf("Loaded data for %,d symbols%n%n", dailyBySym.size());

        Files.createDirectories(Paths.get(outputDir));

        // Determine which timeframes to run
        List<TF> tfsToRun = new ArrayList<>();
        if (filterTF.equals("all") || filterTF.equals("yearly"))    tfsToRun.add(TF.YEARLY);
        if (filterTF.equals("all") || filterTF.equals("quarterly")) tfsToRun.add(TF.QUARTERLY);
        if (filterTF.equals("all") || filterTF.equals("monthly"))   tfsToRun.add(TF.MONTHLY);

        for (TF tf : tfsToRun) {
            System.out.println("=".repeat(60));
            System.out.println("TIMEFRAME: " + tfLabel(tf));
            System.out.println("=".repeat(60));

            if (mode.equals("backtest") || mode.equals("both")) {
                List<Signal> sigs = runBacktest(dailyBySym, tf);
                String path = outputDir + "/backtest_" + tf.name().toLowerCase() + ".csv";
                writeBacktestCSV(sigs, path);
                printBacktestSummary(sigs, tf);
            }

            if (mode.equals("scan") || mode.equals("both")) {
                List<Signal> live = runLiveScanner(dailyBySym, tf);
                String path = outputDir + "/live_" + tf.name().toLowerCase() + ".csv";
                writeScannerCSV(live, path);
                printLiveSummary(live, tf);
            }
            System.out.println();
        }

        System.out.println("Done. Output written to: " + outputDir);
    }

    // ============================================================
    // CANDLE BUILDERS
    // ============================================================

    // Build YEARLY candles from daily bars
    static List<Candle> buildYearly(String symbol, List<Bar> bars) {
        Map<Integer, List<Bar>> byYear = new LinkedHashMap<>();
        for (Bar b : bars) byYear.computeIfAbsent(b.date.getYear(), k->new ArrayList<>()).add(b);
        List<Candle> out = new ArrayList<>();
        for (Map.Entry<Integer,List<Bar>> e : byYear.entrySet())
            out.add(aggregate(symbol, String.valueOf(e.getKey()), "YEARLY", e.getValue()));
        return out;
    }

    // Build QUARTERLY candles from daily bars
    static List<Candle> buildQuarterly(String symbol, List<Bar> bars) {
        Map<String, List<Bar>> byQ = new LinkedHashMap<>();
        for (Bar b : bars) {
            int q = (b.date.getMonthValue()-1)/3+1;
            String key = b.date.getYear()+"-Q"+q;
            byQ.computeIfAbsent(key, k->new ArrayList<>()).add(b);
        }
        List<Candle> out = new ArrayList<>();
        for (Map.Entry<String,List<Bar>> e : byQ.entrySet())
            out.add(aggregate(symbol, e.getKey(), "QUARTERLY", e.getValue()));
        return out;
    }

    // Build MONTHLY candles from daily bars
    static List<Candle> buildMonthly(String symbol, List<Bar> bars) {
        Map<String, List<Bar>> byM = new LinkedHashMap<>();
        for (Bar b : bars) {
            String key = b.date.getYear()+"-"+String.format("%02d",b.date.getMonthValue());
            byM.computeIfAbsent(key, k->new ArrayList<>()).add(b);
        }
        List<Candle> out = new ArrayList<>();
        for (Map.Entry<String,List<Bar>> e : byM.entrySet())
            out.add(aggregate(symbol, e.getKey(), "MONTHLY", e.getValue()));
        return out;
    }

    // Build WEEKLY candles from daily bars (ISO week)
    static List<Candle> buildWeekly(String symbol, List<Bar> bars) {
        Map<String, List<Bar>> byW = new LinkedHashMap<>();
        for (Bar b : bars) {
            int woy = b.date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int wyr = b.date.get(WeekFields.ISO.weekBasedYear());
            String key = wyr+"-W"+String.format("%02d",woy);
            byW.computeIfAbsent(key, k->new ArrayList<>()).add(b);
        }
        List<Candle> out = new ArrayList<>();
        for (Map.Entry<String,List<Bar>> e : byW.entrySet())
            out.add(aggregate(symbol, e.getKey(), "WEEKLY", e.getValue()));
        return out;
    }

    // Aggregate a list of bars into one candle
    static Candle aggregate(String symbol, String key, String tfName, List<Bar> bars) {
        Candle c = new Candle(); c.symbol=symbol; c.periodKey=key; c.tfName=tfName;
        c.start=bars.get(0).date; c.end=bars.get(bars.size()-1).date;
        c.open=bars.get(0).open; c.close=bars.get(bars.size()-1).close;
        c.high=Double.MIN_VALUE; c.low=Double.MAX_VALUE; c.bars=bars.size();
        for (Bar b : bars) {
            if (b.high>c.high){c.high=b.high;c.highDate=b.date;}
            if (b.low<c.low) {c.low=b.low; c.lowDate=b.date;}
            c.totalVol+=b.volume; c.totalDeliv+=b.delivQty; c.avgDelivPer+=b.delivPer;
        }
        c.avgDelivPer/=bars.size();
        if (c.highDate!=null&&c.lowDate!=null) {
            if      (c.highDate.isBefore(c.lowDate)) c.trend="DOWN";
            else if (c.lowDate.isBefore(c.highDate)) c.trend="UP";
            else    c.trend="FLAT";
        } else c.trend="FLAT";
        c.closeAboveOpen=c.close>c.open;
        double rng=c.fullRange();
        c.bodyPct=rng>0?c.bodySize()/rng*100.0:0;
        return c;
    }

    // ============================================================
    // TIMEFRAME PAIR RESOLVER
    // Returns [t1Candles, t2Candles] for the given TF
    // TF.YEARLY     -> T1=yearly,    T2=quarterly
    // TF.QUARTERLY  -> T1=quarterly, T2=monthly
    // TF.MONTHLY    -> T1=monthly,   T2=weekly
    // ============================================================
    static List<Candle>[] buildCandlePair(String sym, List<Bar> bars, TF tf) {
        List<Candle> t1, t2;
        switch (tf) {
            case YEARLY:
                t1 = buildYearly(sym, bars);
                t2 = buildQuarterly(sym, bars);
                break;
            case QUARTERLY:
                t1 = buildQuarterly(sym, bars);
                t2 = buildMonthly(sym, bars);
                break;
            case MONTHLY:
            default:
                t1 = buildMonthly(sym, bars);
                t2 = buildWeekly(sym, bars);
                break;
        }
        @SuppressWarnings("unchecked")
        List<Candle>[] pair = new List[]{t1, t2};
        return pair;
    }

    // Get the T2 candles that belong inside a T1 candle period
    static List<Candle> getT2ForT1(Candle t1, List<Candle> allT2) {
        return allT2.stream()
                .filter(t2 -> !t2.start.isBefore(t1.start) && !t2.end.isAfter(t1.end))
                .sorted(Comparator.comparing(c -> c.start))
                .collect(Collectors.toList());
    }

    // Get the first T2 candle that starts AFTER T1 ends (kept for reference, not used in main logic)
    static Candle getFirstT2AfterT1(Candle t1, List<Candle> allT2) {
        return allT2.stream()
                .filter(t2 -> t2.start.isAfter(t1.end))
                .sorted(Comparator.comparing(c -> c.start))
                .findFirst().orElse(null);
    }

    // ============================================================
    // GATE CHECK (monthly/weekly level confirmation)
    // ============================================================
    static boolean sellGatePasses(Candle t1, Candle t2) {
        if (!t1.trend.equals("DOWN") || t1.closeAboveOpen) return false;
        double dist = Math.abs(t2.high - t1.open) / t1.open * 100.0;
        return dist <= RETEST_TOLERANCE_PCT && t2.close < t1.open;
    }

    static boolean buyGatePasses(Candle t1, Candle t2) {
        if (!t1.trend.equals("UP") || !t1.closeAboveOpen) return false;
        double dist = Math.abs(t2.low - t1.open) / t1.open * 100.0;
        return dist <= RETEST_TOLERANCE_PCT && t2.close > t1.open;
    }

    // ============================================================
    // DAILY ENTRY SCAN (find exact entry candle within T2 period)
    // ============================================================
    static Signal findSellEntry(String sym, TF tf, Candle t1, Candle t2,
                                List<Bar> allDaily) {
        List<Bar> t2Bars = getDailyBarsInPeriod(allDaily, t2.start, t2.end);
        double qOpen = t1.open;
        boolean touched = false; Bar retestBar=null, entryBar=null;

        for (Bar b : t2Bars) {
            if (!touched) {
                double dist = Math.abs(b.high - qOpen) / qOpen * 100.0;
                if (dist <= RETEST_TOLERANCE_PCT) { touched=true; retestBar=b; }
            }
            if (touched && b.close < qOpen) { entryBar=b; break; }
            if (touched && b.close > qOpen*1.01) { touched=false; retestBar=null; }
        }

        Signal s = buildSignal(sym, tf, "SELL", t1, t2);
        if (s == null) return null;
        if (retestBar!=null && entryBar!=null) {
            s.retestDate=retestBar.date; s.retestPrice=retestBar.high;
            s.entryDate=entryBar.date;  s.entryClose=entryBar.close;
            s.sameCandle=retestBar.date.equals(entryBar.date);
        } else {
            // Fallback: use T2 high date / close date
            s.retestDate=t2.highDate; s.retestPrice=t2.high;
            s.entryDate=t2.lowDate!=null?t2.lowDate:t2.end;
            s.entryClose=t2.close; s.sameCandle=false;
        }
        return s;
    }

    static Signal findBuyEntry(String sym, TF tf, Candle t1, Candle t2,
                               List<Bar> allDaily) {
        List<Bar> t2Bars = getDailyBarsInPeriod(allDaily, t2.start, t2.end);
        double qOpen = t1.open;
        boolean touched = false; Bar retestBar=null, entryBar=null;

        for (Bar b : t2Bars) {
            if (!touched) {
                double dist = Math.abs(b.low - qOpen) / qOpen * 100.0;
                if (dist <= RETEST_TOLERANCE_PCT) { touched=true; retestBar=b; }
            }
            if (touched && b.close > qOpen) { entryBar=b; break; }
            if (touched && b.close < qOpen*0.99) { touched=false; retestBar=null; }
        }

        Signal s = buildSignal(sym, tf, "BUY", t1, t2);
        if (s == null) return null;
        if (retestBar!=null && entryBar!=null) {
            s.retestDate=retestBar.date; s.retestPrice=retestBar.low;
            s.entryDate=entryBar.date;  s.entryClose=entryBar.close;
            s.sameCandle=retestBar.date.equals(entryBar.date);
        } else {
            s.retestDate=t2.lowDate; s.retestPrice=t2.low;
            s.entryDate=t2.highDate!=null?t2.highDate:t2.end;
            s.entryClose=t2.close; s.sameCandle=false;
        }
        return s;
    }

    static Signal buildSignal(String sym, TF tf, String side, Candle t1, Candle t2) {
        Signal s = new Signal(); s.symbol=sym; s.timeframe=tf; s.tfLabel=tfLabel(tf);
        s.side=side; s.t1Period=t1.periodKey; s.t2Period=t2.periodKey;
        s.t1Open=t1.open; s.t1High=t1.high; s.t1Low=t1.low;
        s.t1Trend=t1.trend; s.t2Trend=t2.trend; s.t2BodyPct=t2.bodyPct;
        s.t2RetestDistPct = side.equals("SELL")
                ? Math.abs(t2.high-t1.open)/t1.open*100.0
                : Math.abs(t2.low-t1.open)/t1.open*100.0;
        if (side.equals("SELL")) {
            s.riskAmt=Math.abs(t1.open-t1.high); s.rewardAmt=Math.abs(t1.open-t1.low);
        } else {
            s.riskAmt=Math.abs(t1.open-t1.low); s.rewardAmt=Math.abs(t1.high-t1.open);
        }
        s.rr = s.riskAmt>0 ? Math.round(s.rewardAmt/s.riskAmt*100.0)/100.0 : 0;
        if (s.rr < MIN_RR) return null;
        return s;
    }

    // ============================================================
    // BACKTEST
    // ============================================================
    static List<Signal> runBacktest(Map<String,List<Bar>> dailyBySym, TF tf) {
        List<Signal> signals = new ArrayList<>();

        for (Map.Entry<String,List<Bar>> e : dailyBySym.entrySet()) {
            String sym = e.getKey();
            List<Bar> bars = e.getValue();

            List<Candle>[] pair = buildCandlePair(sym, bars, tf);
            List<Candle> t1List = pair[0];
            List<Candle> t2List = pair[1];

            t1List.sort(Comparator.comparing(c->c.start));
            t2List.sort(Comparator.comparing(c->c.start));

            for (int i = 0; i < t1List.size(); i++) {
                Candle t1 = t1List.get(i);

                // T2 and T3 are the 2nd and 3rd candles INSIDE the same T1 period
                // e.g. QUARTERLY->MONTHLY: T1=quarter, T2=month2, T3=month3 of same quarter
                //      YEARLY->QUARTERLY:  T1=year,    T2=Q2,     T3=Q3 of same year
                //      MONTHLY->WEEKLY:    T1=month,   T2=week2,  T3=week3 of same month
                List<Candle> innerCandles = getT2ForT1(t1, t2List);
                if (innerCandles.size() < 2) continue;

                // ref  = first inner candle (M1/Q1/W1) -- provides open, high, low references
                // t2   = second inner candle (M2/Q2/W2) -- the retest candle
                // t3   = third inner candle  (M3/Q3/W3) -- the outcome candle
                Candle ref = innerCandles.get(0); // M1: open=entry, high=stop(sell), low=stop(buy)
                Candle t2  = innerCandles.get(1); // M2: retest candle
                Candle t3  = innerCandles.size() > 2 ? innerCandles.get(2) : null; // M3: outcome

                // SELL: ref.open = entry, ref.high = stop, ref.low = target
                if (sellGatePasses(ref, t2)) {
                    Signal s = findSellEntry(sym, tf, ref, t2, bars);
                    if (s != null) { fillOutcome(s, t3); signals.add(s); }
                }
                // BUY: ref.open = entry, ref.low = stop, ref.high = target
                if (buyGatePasses(ref, t2)) {
                    Signal s = findBuyEntry(sym, tf, ref, t2, bars);
                    if (s != null) { fillOutcome(s, t3); signals.add(s); }
                }
            }
        }
        return signals;
    }

    static Candle getNextCandle(Candle ref, List<Candle> list) {
        return list.stream()
                .filter(c -> c.start.isAfter(ref.end))
                .sorted(Comparator.comparing(c->c.start))
                .findFirst().orElse(null);
    }

    static void fillOutcome(Signal s, Candle t3) {
        if (t3==null){s.backtestComplete=false;s.outcome="OPEN";return;}
        s.backtestComplete=true; s.t3Low=t3.low; s.t3High=t3.high;
        if (s.side.equals("SELL")) {
            boolean tgt=t3.low<s.t1Low, stp=t3.high>s.t1High;
            if (tgt&&!stp){s.targetHit=true;s.outcome="WIN";}
            else if(stp&&!tgt){s.stopHit=true;s.outcome="LOSS";}
            else if(tgt){s.targetHit=true;s.outcome="WIN";}
            else s.outcome="BREAKEVEN";
        } else {
            boolean tgt=t3.high>s.t1High, stp=t3.low<s.t1Low;
            if (tgt&&!stp){s.targetHit=true;s.outcome="WIN";}
            else if(stp&&!tgt){s.stopHit=true;s.outcome="LOSS";}
            else if(tgt){s.targetHit=true;s.outcome="WIN";}
            else s.outcome="BREAKEVEN";
        }
    }

    // ============================================================
    // LIVE SCANNER
    // ============================================================
    static List<Signal> runLiveScanner(Map<String,List<Bar>> dailyBySym, TF tf) {
        List<Signal> live = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Map.Entry<String,List<Bar>> e : dailyBySym.entrySet()) {
            String sym = e.getKey();
            List<Bar> bars = e.getValue();
            if (bars.isEmpty()) continue;

            List<Candle>[] pair = buildCandlePair(sym, bars, tf);
            List<Candle> t1List = pair[0];
            List<Candle> t2List = pair[1];
            t1List.sort(Comparator.comparing(c->c.start));
            t2List.sort(Comparator.comparing(c->c.start));

            if (t1List.isEmpty()) continue;

            // Get the most recent completed T1
            Candle t1 = t1List.get(t1List.size()-1);
            // If T1 is still in progress (end == today area), use second-to-last
            if (!t1.end.isBefore(today.withDayOfMonth(1)) && t1List.size()>1)
                t1 = t1List.get(t1List.size()-2);

            if (t1 == null) continue;

            // Get the current T2 (retest period -- first of next T1)
            // T2 = 2nd candle inside T1 period (retest candle)
            List<Candle> innerT2 = getT2ForT1(t1, t2List);
            Candle t2Completed = innerT2.size() >= 2 ? innerT2.get(1) : null;

            // T2 runs inside T1 period -- get daily bars for the T2 sub-period
            // T2 starts after T1's first inner candle ends
            Candle firstInner = innerT2.size() >= 1 ? innerT2.get(0) : null;
            LocalDate t2Start = firstInner != null ? firstInner.end.plusDays(1) : t1.start;
            List<Bar> t2RunBars = getDailyBarsInPeriod(bars, t2Start, today);

            // Get the first inner candle (ref) -- provides open/high/low for entry/stop/target
            List<Candle> liveInner = getT2ForT1(t1, t2List);
            Candle ref = liveInner.isEmpty() ? t1 : liveInner.get(0);

            // ── SELL ──
            if (ref.trend.equals("DOWN") && !ref.closeAboveOpen) {
                double qOpen = ref.open;

                if (t2Completed != null && sellGatePasses(ref, t2Completed)) {
                    Signal s = findSellEntry(sym, tf, ref, t2Completed, bars);
                    if (s != null) {
                        s.outcome = "SIGNAL ACTIVE - Enter SELL at " + fmt(qOpen);
                        live.add(s); continue;
                    }
                }

                if (!t2RunBars.isEmpty()) {
                    double runHigh = t2RunBars.stream().mapToDouble(b->b.high).max().orElse(0);
                    double dist    = Math.abs(runHigh - qOpen) / qOpen * 100.0;
                    double lastCl  = t2RunBars.get(t2RunBars.size()-1).close;

                    if (dist <= RETEST_TOLERANCE_PCT && lastCl < qOpen) {
                        Candle proxy = buildProxyCandle(sym, t2RunBars);
                        Signal s = findSellEntry(sym, tf, ref, proxy, bars);
                        if (s != null) {
                            s.outcome = "LIVE SIGNAL FIRED - Sell at " + fmt(qOpen);
                            live.add(s); continue;
                        }
                    }
                    Signal w = buildSignal(sym, tf, "SELL", ref, buildProxyCandle(sym, t2RunBars));
                    if (w != null) {
                        w.outcome = dist<=RETEST_TOLERANCE_PCT && lastCl>=qOpen
                                ? "RETEST IN PROGRESS - Wait for close below " + fmt(qOpen)
                                : "WATCHING - M1 DOWN. Q_OPEN=" + fmt(qOpen) + ". Retest not yet.";
                        live.add(w);
                    }
                }
            }

            // ── BUY ──
            if (ref.trend.equals("UP") && ref.closeAboveOpen) {
                double qOpen = ref.open;

                if (t2Completed != null && buyGatePasses(ref, t2Completed)) {
                    Signal s = findBuyEntry(sym, tf, ref, t2Completed, bars);
                    if (s != null) {
                        s.outcome = "SIGNAL ACTIVE - Enter BUY at " + fmt(qOpen);
                        live.add(s); continue;
                    }
                }

                if (!t2RunBars.isEmpty()) {
                    double runLow = t2RunBars.stream().mapToDouble(b->b.low).min().orElse(Double.MAX_VALUE);
                    double dist   = Math.abs(runLow - qOpen) / qOpen * 100.0;
                    double lastCl = t2RunBars.get(t2RunBars.size()-1).close;

                    if (dist <= RETEST_TOLERANCE_PCT && lastCl > qOpen) {
                        Candle proxy = buildProxyCandle(sym, t2RunBars);
                        Signal s = findBuyEntry(sym, tf, ref, proxy, bars);
                        if (s != null) {
                            s.outcome = "LIVE SIGNAL FIRED - Buy at " + fmt(qOpen);
                            live.add(s); continue;
                        }
                    }
                    Signal w = buildSignal(sym, tf, "BUY", ref, buildProxyCandle(sym, t2RunBars));
                    if (w != null) {
                        w.outcome = dist<=RETEST_TOLERANCE_PCT && lastCl<=qOpen
                                ? "RETEST IN PROGRESS - Wait for close above " + fmt(qOpen)
                                : "WATCHING - M1 UP. Q_OPEN=" + fmt(qOpen) + ". Pullback not yet.";
                        live.add(w);
                    }
                }
            }
        }
        return live;
    }

    static Candle buildProxyCandle(String sym, List<Bar> bars) {
        if (bars.isEmpty()) {
            Candle c=new Candle(); c.symbol=sym; c.trend="FLAT"; return c;
        }
        return aggregate(sym, "IN_PROGRESS", "IN_PROGRESS", bars);
    }

    // ============================================================
    // WRITE CSVs
    // ============================================================
    static void writeBacktestCSV(List<Signal> sigs, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,TIMEFRAME,T1_PERIOD,T2_PERIOD,SIDE," +
                    "T1_OPEN_ENTRY,STOP,TARGET,RISK_AMT,REWARD_AMT,RR," +
                    "T2_GATE_DIST_PCT,RETEST_DATE,RETEST_PRICE," +
                    "ENTRY_DATE,ENTRY_CLOSE,SAME_CANDLE," +
                    "T1_TREND,T2_TREND,T2_BODY_PCT," +
                    "T3_LOW,T3_HIGH,OUTCOME,TARGET_HIT,STOP_HIT,OPTIONS_ACTION");
            for (Signal s : sigs) {
                pw.printf("%s,%s,%s,%s,%s," +
                                "%.2f,%s,%s,%.2f,%.2f,%.2f," +
                                "%.2f,%s,%.2f," +
                                "%s,%.2f,%s," +
                                "%s,%s,%.2f," +
                                "%.2f,%.2f,%s,%s,%s,%s%n",
                        s.symbol,s.tfLabel,s.t1Period,s.t2Period,s.side,
                        s.t1Open,s.stopLevel(),s.targetLevel(),s.riskAmt,s.rewardAmt,s.rr,
                        s.t2RetestDistPct,s.retestDate!=null?s.retestDate:"",s.retestPrice,
                        s.entryDate!=null?s.entryDate:"",s.entryClose,s.sameCandle?"YES":"NO",
                        s.t1Trend,s.t2Trend!=null?s.t2Trend:"",s.t2BodyPct,
                        s.t3Low,s.t3High,
                        s.outcome!=null?s.outcome:"OPEN",
                        s.targetHit?"YES":"NO",s.stopHit?"YES":"NO",
                        s.optionsAction());
            }
        }
        System.out.printf("Written: %s  (%,d signals)%n", path, sigs.size());
    }

    static void writeScannerCSV(List<Signal> sigs, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,TIMEFRAME,T1_PERIOD,T2_PERIOD,SIDE," +
                    "T1_OPEN_ENTRY,STOP,TARGET,RR," +
                    "RETEST_DATE,ENTRY_DATE,ENTRY_CLOSE,SAME_CANDLE," +
                    "STATUS,OPTIONS_ACTION");
            for (Signal s : sigs) {
                pw.printf("%s,%s,%s,%s,%s," +
                                "%.2f,%s,%s,%.2f," +
                                "%s,%s,%.2f,%s," +
                                "%s,%s%n",
                        s.symbol,s.tfLabel,s.t1Period,s.t2Period,s.side,
                        s.t1Open,s.stopLevel(),s.targetLevel(),s.rr,
                        s.retestDate!=null?s.retestDate:"",
                        s.entryDate!=null?s.entryDate:"",
                        s.entryClose,s.sameCandle?"YES":"NO",
                        s.outcome!=null?s.outcome:"",
                        s.optionsAction());
            }
        }
        System.out.printf("Written: %s  (%,d signals)%n", path, sigs.size());
    }

    // ============================================================
    // CONSOLE SUMMARIES
    // ============================================================
    static void printBacktestSummary(List<Signal> sigs, TF tf) {
        System.out.println();
        System.out.println("--- BACKTEST: " + tfLabel(tf) + " ---");
        for (String side : new String[]{"SELL","BUY"}) {
            List<Signal> ss = sigs.stream()
                    .filter(x->x.side.equals(side)&&x.backtestComplete).collect(Collectors.toList());
            if (ss.isEmpty()) continue;
            long wins=ss.stream().filter(x->x.outcome.equals("WIN")).count();
            long loss=ss.stream().filter(x->x.outcome.equals("LOSS")).count();
            long total=ss.size();
            double wr=wins*100.0/total;
            double avgRR=ss.stream().mapToDouble(x->x.rr).average().orElse(0);
            long same=ss.stream().filter(x->x.sameCandle).count();
            double expectancy=wr/100.0*avgRR - (1-wr/100.0)*1;
            System.out.printf("%n%s SETUP:%n",side);
            System.out.printf("  Total signals    : %,d%n",
                    sigs.stream().filter(x->x.side.equals(side)).count());
            System.out.printf("  Completed        : %,d%n",total);
            System.out.printf("  Win rate         : %.2f%%  (%,d W / %,d L)%n",wr,wins,loss);
            System.out.printf("  Avg R:R          : %.2f:1%n",avgRR);
            System.out.printf("  Expectancy       : +%.2fR per trade%n",expectancy);
            System.out.printf("  Same-candle      : %,d / %,d%n",same,total);
        }
    }

    static void printLiveSummary(List<Signal> sigs, TF tf) {
        System.out.println();
        System.out.println("--- LIVE SIGNALS: " + tfLabel(tf) + " ---");
        long fired   =sigs.stream().filter(s->s.outcome!=null&&(s.outcome.contains("FIRED")||s.outcome.contains("ACTIVE"))).count();
        long retest  =sigs.stream().filter(s->s.outcome!=null&&s.outcome.contains("RETEST IN PROGRESS")).count();
        long watching=sigs.stream().filter(s->s.outcome!=null&&s.outcome.contains("WATCHING")).count();
        System.out.printf("  Enter now     : %,d%n",fired);
        System.out.printf("  Retest watch  : %,d%n",retest);
        System.out.printf("  Watching      : %,d%n",watching);
        sigs.stream()
                .filter(s->s.outcome!=null&&(s.outcome.contains("FIRED")||s.outcome.contains("ACTIVE")))
                .limit(20)
                .forEach(s->System.out.printf("  %-14s %-22s %-5s  Entry=%-10.2f  Stop=%-10s  Target=%-10s  R:R=%.2f%n",
                        s.symbol,s.tfLabel,s.side,s.t1Open,s.stopLevel(),s.targetLevel(),s.rr));
    }

    // ============================================================
    // LOAD BHAVCOPY
    // ============================================================
    static Map<String, List<Bar>> loadBhavcopies(String folder, String filterSym) {
        Map<String, List<Bar>> raw = new LinkedHashMap<>();
        File dir = new File(folder);
        if (!dir.exists()) {
            System.out.println("Folder not found. Generating sample data...");
            try { generateSampleData(folder); dir=new File(folder); }
            catch (Exception e) { System.out.println(e.getMessage()); return raw; }
        }
        File[] files = dir.listFiles((d,n)->n.toLowerCase().endsWith(".csv"));
        if (files==null||files.length==0){System.out.println("No CSV files found.");return raw;}
        Arrays.sort(files,Comparator.comparing(File::getName));
        System.out.printf("Found %d files. Loading...%n",files.length);
        int done=0;
        for (File f : files) {
            for (Bar b : parseBhavcopy(f,filterSym))
                raw.computeIfAbsent(b.symbol,k->new ArrayList<>()).add(b);
            done++;
            if (done%100==0) System.out.printf("  %d / %d...%n",done,files.length);
        }
        System.out.printf("  Loaded all %d files.%n%n",done);
        for (List<Bar> bars:raw.values()) bars.sort(Comparator.comparing(b->b.date));
        return raw;
    }

    static List<Bar> parseBhavcopy(File file, String filterSym) {
        List<Bar> bars = new ArrayList<>();
        try (BufferedReader br=new BufferedReader(new FileReader(file))) {
            String line; boolean header=true;
            while ((line=br.readLine())!=null) {
                line=line.trim(); if(line.isEmpty())continue;
                if(header){header=false;if(line.toUpperCase().contains("SYMBOL"))continue;}
                String[] c=line.split(",");
                for(int i=0;i<c.length;i++)c[i]=c[i].trim();
                if(c.length<13)continue;
                try {
                    String sym=c[0].toUpperCase(),ser=c[1].toUpperCase();
                    if(!ser.equals("EQ")&&!ser.equals("BE")&&!ser.equals("BZ"))continue;
                    if(!filterSym.isEmpty()&&!sym.equals(filterSym))continue;
                    LocalDate date=LocalDate.parse(c[2].trim(),DATE_FMT);
                    double open=sd(c[4]),high=sd(c[5]),low=sd(c[6]),close=sd(c[7]);
                    if(open<=0||high<=0||low<=0||close<=0||high<low)continue;
                    Bar b=new Bar(); b.symbol=sym; b.date=date;
                    b.open=open;b.high=high;b.low=low;b.close=close;
                    b.volume=sl(c[10]);
                    b.delivQty=c.length>13?sl(c[13]):0L;
                    b.delivPer=c.length>14?sd(c[14]):0.0;
                    bars.add(b);
                } catch(Exception ignored){}
            }
        } catch(Exception e){System.out.println("  Warning: "+file.getName());}
        return bars;
    }

    // ============================================================
    // HELPERS
    // ============================================================
    static List<Bar> getDailyBarsInPeriod(List<Bar> bars, LocalDate from, LocalDate to) {
        return bars.stream()
                .filter(b->!b.date.isBefore(from)&&!b.date.isAfter(to))
                .sorted(Comparator.comparing(b->b.date))
                .collect(Collectors.toList());
    }

    static String tfLabel(TF tf) {
        switch(tf){
            case YEARLY:    return "YEARLY->QUARTERLY";
            case QUARTERLY: return "QUARTERLY->MONTHLY";
            case MONTHLY:   return "MONTHLY->WEEKLY";
            default:        return tf.name();
        }
    }

    static String fmt(double v){return String.format("%.2f",v);}
    static double sd(String s){try{return Double.parseDouble(s.trim().replace(",",""));}catch(Exception e){return 0;}}
    static long sl(String s){try{String t=s.trim().replace(",","");if(t.contains("."))t=t.split("\\.")[0];return Long.parseLong(t);}catch(Exception e){return 0L;}}

    // ============================================================
    // SAMPLE DATA GENERATOR
    // ============================================================
    static void generateSampleData(String folderPath) throws Exception {
        Files.createDirectories(Paths.get(folderPath));
        String[] syms={"RELIANCE","TCS","INFY","HDFCBANK","SBIN","MARUTI"};
        Random rand=new Random(42); Map<String,Double> prices=new HashMap<>();
        for(String s:syms)prices.put(s,500+rand.nextDouble()*2000);
        LocalDate start=LocalDate.of(2021,1,4),end=LocalDate.now();
        Map<LocalDate,List<String[]>> byDate=new TreeMap<>();
        LocalDate cur=start;
        while(!cur.isAfter(end)){
            DayOfWeek dow=cur.getDayOfWeek();
            if(dow!=DayOfWeek.SATURDAY&&dow!=DayOfWeek.SUNDAY){
                List<String[]> rows=new ArrayList<>();
                for(String sym:syms){
                    double price=prices.get(sym),chg=price*(rand.nextGaussian()*0.015);
                    double open=Math.max(1,price+chg*0.3);
                    double hi=open+Math.abs(rand.nextGaussian()*price*0.012);
                    double lo=open-Math.abs(rand.nextGaussian()*price*0.012);
                    double close=Math.max(lo,Math.min(hi,open+chg));
                    long vol=50000+(long)(rand.nextDouble()*1000000);
                    long del=(long)(vol*(0.3+rand.nextDouble()*0.5));
                    prices.put(sym,close);
                    String ds=cur.format(DateTimeFormatter.ofPattern("d-MMM-yyyy",Locale.ENGLISH));
                    rows.add(new String[]{sym,"EQ",ds,fmt(price),fmt(open),fmt(hi),fmt(lo),
                            fmt(close),fmt(close),fmt((open+hi+lo+close)/4),String.valueOf(vol),
                            fmt(vol*close/100000),String.valueOf(1000+rand.nextInt(9000)),
                            String.valueOf(del),fmt(del*100.0/vol)});
                }
                byDate.put(cur,rows);
            }
            cur=cur.plusDays(1);
        }
        String hdr="SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER";
        for(Map.Entry<LocalDate,List<String[]>> e:byDate.entrySet()){
            String fn=folderPath+"/bhav_"+e.getKey().format(DateTimeFormatter.ofPattern("ddMMMyyyy",Locale.ENGLISH)).toUpperCase()+".csv";
            try(PrintWriter pw=new PrintWriter(new FileWriter(fn))){
                pw.println(hdr); for(String[] row:e.getValue())pw.println(String.join(", ",row));
            }
        }
        System.out.printf("Generated %d sample files in: %s%n%n",byDate.size(),folderPath);
    }

    static void printBanner() {
        System.out.println("+============================================================+");
        System.out.println("|   Multi-Timeframe Retest Setup Scanner                     |");
        System.out.println("|   YEARLY->QTR  |  QUARTERLY->MON  |  MONTHLY->WEEKLY       |");
        System.out.println("+============================================================+");
        System.out.println();
    }

    static void printUsage() {
        System.out.println("USAGE:");
        System.out.println("  javac SetupScanner.java");
        System.out.println("  java SetupScanner <mode> <bhavcopy_folder> <output_folder> [SYMBOL] [TIMEFRAME]");
        System.out.println();
        System.out.println("MODES      : backtest | scan | both");
        System.out.println("TIMEFRAMES : all | yearly | quarterly | monthly");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  java SetupScanner both ./data ./output");
        System.out.println("  java SetupScanner both ./data ./output ALL quarterly");
        System.out.println("  java SetupScanner both ./data ./output RELIANCE monthly");
        System.out.println("  java SetupScanner scan ./data ./output ALL yearly");
    }
}