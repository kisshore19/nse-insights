package com.kisshore19.nseinsights.backtesting;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

/**
 * EarlyQuarterPredictor
 * -------------------------------------------------------------------------
 * Reads monthly_ohlc.csv (output of BhavcopyProcessor)
 * For every stock, generates early quarter predictions BEFORE the quarter ends
 *
 * CONFIDENCE LADDER (from real NSE backtesting):
 *   Month 1 alone              -> 64.65%  (watchlist only)
 *   Month 1 body > 75% range  -> 79.10%  (first signal)
 *   Month 2 alone              -> 74.03%  (small position)
 *   Month 1 + Month 2 agree   -> 86.65%  (PRIME ENTRY -- full size options sell)
 *   All 3 months agree         -> 96.18%  (near certainty -- add to position)
 *
 * WHEN M1 AND M2 DISAGREE:
 *   M1 UP  + M2 DOWN -> Quarter likely DOWN (55.6%) -- trust M2
 *   M1 DOWN + M2 UP  -> Quarter likely UP   (65.0%) -- recovery signal
 *
 * OUTPUT:
 *   early_predictions.csv     -- all predictions with confidence scores
 *   prime_signals.csv         -- only M1+M2 agree (86.65%+ confidence)
 *   current_quarter_report.csv-- current quarter signals for action today
 *
 * USAGE:
 *   javac EarlyQuarterPredictor.java
 *   java EarlyQuarterPredictor <monthly_ohlc.csv> <output_folder>
 *   java EarlyQuarterPredictor <monthly_ohlc.csv> <output_folder> [SYMBOL]
 * -------------------------------------------------------------------------
 */
public class EarlyQuarterPredictor {

    // -- CONFIDENCE SCORES (from backtesting) ---------------------------------
    static final double CONF_M1_ALONE        = 64.65;
    static final double CONF_M1_LARGE_BODY   = 79.10;
    static final double CONF_M2_ALONE        = 74.03;
    static final double CONF_M1_M2_AGREE     = 86.65;
    static final double CONF_ALL3_AGREE      = 96.18;
    static final double CONF_M1UP_M2DN       = 55.60; // trust M2, Q likely DOWN
    static final double CONF_M1DN_M2UP       = 65.00; // recovery, Q likely UP

    // -- MONTHLY RECORD -------------------------------------------------------
    static class MonthlyCandle {
        String    symbol;
        int       year;
        int       month;
        String    period;       // "2024-07"
        double    open, high, low, close;
        double    bodyPct;      // body as % of full range
        double    avgDelivPer;
        long      totalVolume;
        String    trendDirection;   // UP / DOWN / FLAT
        boolean   closedAboveOpen;
        boolean   trendMatchesBody;
    }

    // -- PREDICTION RECORD ----------------------------------------------------
    static class QuarterPrediction {
        String symbol;
        int    year;
        int    quarter;
        String quarterPeriod;   // e.g. "2024-Q3"

        // Monthly candles
        MonthlyCandle m1, m2, m3;

        // Signal
        String  predictedTrend;     // UP / DOWN / UNCERTAIN
        double  confidencePct;
        String  signalType;         // M1_ONLY / M2_ONLY / M1_M2_AGREE / ALL3_AGREE / M1M2_DISAGREE
        String  actionRecommended;  // WATCHLIST / SMALL_POSITION / FULL_POSITION / ADD_POSITION / WAIT

        // Actual (if quarter is complete)
        boolean quarterComplete;
        String  actualTrend;
        boolean predictionCorrect;

        // Entry timing
        String  entryWindow;        // which month the signal fired

        String optionsAction() {
            if (predictedTrend.equals("DOWN")) return "SELL CALLS";
            if (predictedTrend.equals("UP"))   return "SELL PUTS";
            return "WAIT";
        }
    }

    // -- MAIN -----------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        String monthlyFile  = args.length > 0 ? args[0] : "./output/monthly_ohlc.csv";
        String outputDir    = args.length > 1 ? args[1] : "./predictions";
        String filterSymbol = args.length > 2 ? args[2].toUpperCase().trim() : "";

        printBanner();
        System.out.println("Monthly file : " + monthlyFile);
        System.out.println("Output folder: " + outputDir);
        System.out.println("Symbol filter: " + (filterSymbol.isEmpty() ? "ALL" : filterSymbol));
        System.out.println();

        // 1. Load monthly candles
        List<MonthlyCandle> allMonthly = loadMonthly(monthlyFile, filterSymbol);
        if (allMonthly.isEmpty()) {
            System.out.println("No monthly data found. Check file path.");
            return;
        }

        long uniqueSymbols = allMonthly.stream().map(m -> m.symbol).distinct().count();
        System.out.printf("Loaded %,d monthly candles  |  %,d symbols%n%n",
                allMonthly.size(), uniqueSymbols);

        // 2. Group by symbol
        Map<String, List<MonthlyCandle>> bySymbol = allMonthly.stream()
                .sorted(Comparator.comparing((MonthlyCandle m) -> m.symbol)
                        .thenComparingInt(m -> m.year * 100 + m.month))
                .collect(Collectors.groupingBy(
                        m -> m.symbol,
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 3. Generate predictions
        List<QuarterPrediction> allPredictions = new ArrayList<>();
        for (Map.Entry<String, List<MonthlyCandle>> entry : bySymbol.entrySet()) {
            allPredictions.addAll(generatePredictions(entry.getKey(), entry.getValue()));
        }

        System.out.printf("Generated %,d quarter predictions%n%n", allPredictions.size());

        // 4. Compute backtest accuracy
        computeAccuracy(allPredictions);

        // 5. Write outputs
        Files.createDirectories(Paths.get(outputDir));
        writeAllPredictions(allPredictions,   outputDir + "/early_predictions.csv");
        writePrimeSignals(allPredictions,      outputDir + "/prime_signals.csv");
        writeCurrentQuarter(allPredictions,    outputDir + "/current_quarter_report.csv");

        // 6. Console summary
        printConsoleSummary(allPredictions);

        System.out.println("\nFiles written to: " + outputDir);
        System.out.println("  early_predictions.csv      -- full history");
        System.out.println("  prime_signals.csv          -- M1+M2 agree (86.65%+) only");
        System.out.println("  current_quarter_report.csv -- actionable signals right now");
    }

    // -- LOAD MONTHLY CSV -----------------------------------------------------
    static List<MonthlyCandle> loadMonthly(String filePath, String filterSymbol) {
        List<MonthlyCandle> candles = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (header) { header = false; continue; }
                String[] cols = line.split(",");
                if (cols.length < 17) continue;
                try {
                    String sym = cols[0].trim().toUpperCase();
                    if (!filterSymbol.isEmpty() && !sym.equals(filterSymbol)) continue;

                    String period = cols[1].trim(); // "2024-07"
                    if (!period.matches("\\d{4}-\\d{2}")) continue;

                    MonthlyCandle c = new MonthlyCandle();
                    c.symbol     = sym;
                    c.period     = period;
                    c.year       = Integer.parseInt(period.substring(0, 4));
                    c.month      = Integer.parseInt(period.substring(5, 7));
                    c.open       = safeDouble(cols[5]);
                    c.high       = safeDouble(cols[6]);
                    c.low        = safeDouble(cols[7]);
                    c.close      = safeDouble(cols[8]);
                    c.bodyPct    = safeDouble(cols[12]);
                    c.trendDirection   = cols[15].trim();
                    c.closedAboveOpen  = cols[14].trim().equals("YES");
                    c.trendMatchesBody = cols[16].trim().equals("YES");
                    c.totalVolume      = safeLong(cols[19]);
                    c.avgDelivPer      = cols.length > 22 ? safeDouble(cols[22]) : 0.0;

                    if (c.open <= 0 || c.close <= 0) continue;
                    candles.add(c);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        return candles;
    }

    // -- GENERATE PREDICTIONS FOR ONE SYMBOL ----------------------------------
    static List<QuarterPrediction> generatePredictions(String symbol, List<MonthlyCandle> months) {
        List<QuarterPrediction> predictions = new ArrayList<>();

        // Group months by (year, quarter)
        Map<String, List<MonthlyCandle>> byQuarter = new LinkedHashMap<>();
        for (MonthlyCandle m : months) {
            int q = (m.month - 1) / 3 + 1;
            String key = m.year + "-Q" + q;
            byQuarter.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<MonthlyCandle>> entry : byQuarter.entrySet()) {
            String qPeriod = entry.getKey();
            List<MonthlyCandle> qMonths = entry.getValue();

            // Sort by month
            qMonths.sort(Comparator.comparingInt(m -> m.month));

            MonthlyCandle m1 = qMonths.size() > 0 ? qMonths.get(0) : null;
            MonthlyCandle m2 = qMonths.size() > 1 ? qMonths.get(1) : null;
            MonthlyCandle m3 = qMonths.size() > 2 ? qMonths.get(2) : null;

            if (m1 == null) continue;

            QuarterPrediction pred = new QuarterPrediction();
            pred.symbol        = symbol;
            pred.year          = m1.year;
            pred.quarter       = (m1.month - 1) / 3 + 1;
            pred.quarterPeriod = qPeriod;
            pred.m1 = m1;
            pred.m2 = m2;
            pred.m3 = m3;

            // -- DETERMINE SIGNAL ----------------------------------------------
            assignSignal(pred);

            // -- ACTUAL RESULT (if quarter is complete = 3 months available) --
            pred.quarterComplete = (m3 != null);
            if (pred.quarterComplete) {
                // Determine actual quarter trend from M3 close vs M1 open
                // (proxy for quarterly candle: open of M1, close of M3)
                double qOpen  = m1.open;
                double qClose = m3.close;
                pred.actualTrend = qClose > qOpen ? "UP" : qClose < qOpen ? "DOWN" : "FLAT";
                pred.predictionCorrect =
                        !pred.predictedTrend.equals("UNCERTAIN") &&
                                pred.predictedTrend.equals(pred.actualTrend);
            }

            predictions.add(pred);
        }
        return predictions;
    }

    // -- ASSIGN SIGNAL AND CONFIDENCE -----------------------------------------
    static void assignSignal(QuarterPrediction pred) {
        MonthlyCandle m1 = pred.m1;
        MonthlyCandle m2 = pred.m2;
        MonthlyCandle m3 = pred.m3;

        boolean m1Valid = m1 != null && !m1.trendDirection.equals("FLAT");
        boolean m2Valid = m2 != null && !m2.trendDirection.equals("FLAT");
        boolean m3Valid = m3 != null && !m3.trendDirection.equals("FLAT");

        // -- All 3 months agree --
        if (m1Valid && m2Valid && m3Valid &&
                m1.trendDirection.equals(m2.trendDirection) &&
                m2.trendDirection.equals(m3.trendDirection)) {

            pred.signalType      = "ALL3_AGREE";
            pred.predictedTrend  = m1.trendDirection;
            pred.confidencePct   = CONF_ALL3_AGREE;
            pred.entryWindow     = "Month 3 (late add)";
            pred.actionRecommended = "ADD_POSITION";
            return;
        }

        // -- M1 + M2 agree --
        if (m1Valid && m2Valid && m1.trendDirection.equals(m2.trendDirection)) {
            pred.signalType      = "M1_M2_AGREE";
            pred.predictedTrend  = m1.trendDirection;
            pred.confidencePct   = CONF_M1_M2_AGREE;
            pred.entryWindow     = "After Month 2 closes";
            pred.actionRecommended = "FULL_POSITION";
            return;
        }

        // -- M1 + M2 disagree --
        if (m1Valid && m2Valid && !m1.trendDirection.equals(m2.trendDirection)) {
            pred.signalType = "M1_M2_DISAGREE";
            // Trust M2 -- it is the stronger signal
            if (m1.trendDirection.equals("UP") && m2.trendDirection.equals("DOWN")) {
                pred.predictedTrend  = "DOWN";
                pred.confidencePct   = CONF_M1UP_M2DN;
                pred.entryWindow     = "After Month 2 (M2 reversed M1)";
                pred.actionRecommended = "SMALL_POSITION";
            } else { // M1 DOWN + M2 UP = recovery
                pred.predictedTrend  = "UP";
                pred.confidencePct   = CONF_M1DN_M2UP;
                pred.entryWindow     = "After Month 2 (recovery signal)";
                pred.actionRecommended = "SMALL_POSITION";
            }
            return;
        }

        // -- M2 alone (no M1 or M1 was flat) --
        if (m2Valid && !m1Valid) {
            pred.signalType        = "M2_ONLY";
            pred.predictedTrend    = m2.trendDirection;
            pred.confidencePct     = CONF_M2_ALONE;
            pred.entryWindow       = "After Month 2 closes";
            pred.actionRecommended = "SMALL_POSITION";
            return;
        }

        // -- M1 alone --
        if (m1Valid && !m2Valid) {
            boolean largebody = m1.bodyPct >= 75.0;
            pred.signalType      = largebody ? "M1_LARGE_BODY" : "M1_ONLY";
            pred.predictedTrend  = m1.trendDirection;
            pred.confidencePct   = largebody ? CONF_M1_LARGE_BODY : CONF_M1_ALONE;
            pred.entryWindow     = "After Month 1 closes";
            pred.actionRecommended = largebody ? "WATCHLIST_STRONG" : "WATCHLIST";
            return;
        }

        // -- No valid signal --
        pred.signalType        = "NO_SIGNAL";
        pred.predictedTrend    = "UNCERTAIN";
        pred.confidencePct     = 0;
        pred.entryWindow       = "Wait";
        pred.actionRecommended = "WAIT";
    }

    // -- COMPUTE BACKTEST ACCURACY ---------------------------------------------
    static void computeAccuracy(List<QuarterPrediction> predictions) {
        Map<String, int[]> byType = new LinkedHashMap<>();
        // [correct, total]
        String[] types = {"ALL3_AGREE","M1_M2_AGREE","M1_M2_DISAGREE",
                "M2_ONLY","M1_LARGE_BODY","M1_ONLY"};
        for (String t : types) byType.put(t, new int[]{0, 0});

        int totalCorrect = 0, totalComplete = 0;

        for (QuarterPrediction p : predictions) {
            if (!p.quarterComplete || p.predictedTrend.equals("UNCERTAIN")) continue;
            if (p.actualTrend.equals("FLAT")) continue;

            totalComplete++;
            int[] arr = byType.getOrDefault(p.signalType, new int[]{0,0});
            arr[1]++;
            byType.put(p.signalType, arr);

            if (p.predictionCorrect) {
                totalCorrect++;
                arr[0]++;
            }
        }

        System.out.println("=== BACKTEST ACCURACY ON YOUR DATA ===");
        System.out.printf("%-20s  %8s  %8s  %8s%n", "Signal Type", "Correct", "Total", "Accuracy");
        System.out.println("-".repeat(52));

        for (String t : types) {
            int[] arr = byType.get(t);
            if (arr[1] == 0) continue;
            double acc = arr[0] * 100.0 / arr[1];
            System.out.printf("%-20s  %8s,d  %8s,d  %7.2f%%%n", t, arr[0], arr[1], acc);
        }

        System.out.println("-".repeat(52));
        if (totalComplete > 0)
            System.out.printf("%-20s  %8s,d  %8s,d  %7.2f%%%n",
                    "OVERALL", totalCorrect, totalComplete,
                    totalCorrect * 100.0 / totalComplete);
        System.out.println();
    }

    // -- WRITE ALL PREDICTIONS -------------------------------------------------
    static void writeAllPredictions(List<QuarterPrediction> preds, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,QUARTER,SIGNAL_TYPE,PREDICTED_TREND,CONFIDENCE_PCT," +
                    "ACTION,OPTIONS_ACTION,ENTRY_WINDOW," +
                    "M1_TREND,M1_BODY_PCT,M1_CLOSE_ABOVE_OPEN," +
                    "M2_TREND,M2_BODY_PCT,M2_CLOSE_ABOVE_OPEN," +
                    "M3_TREND,M3_BODY_PCT," +
                    "QUARTER_COMPLETE,ACTUAL_TREND,PREDICTION_CORRECT");
            for (QuarterPrediction p : preds) {
                pw.printf("%s,%s,%s,%s,%.2f,%s,%s,%s," +
                                "%s,%.1f,%s," +
                                "%s,%.1f,%s," +
                                "%s,%.1f," +
                                "%s,%s,%s%n",
                        p.symbol, p.quarterPeriod, p.signalType,
                        p.predictedTrend, p.confidencePct,
                        p.actionRecommended, p.optionsAction(), p.entryWindow,
                        p.m1 != null ? p.m1.trendDirection : "",
                        p.m1 != null ? p.m1.bodyPct : 0,
                        p.m1 != null ? (p.m1.closedAboveOpen ? "YES" : "NO") : "",
                        p.m2 != null ? p.m2.trendDirection : "",
                        p.m2 != null ? p.m2.bodyPct : 0,
                        p.m2 != null ? (p.m2.closedAboveOpen ? "YES" : "NO") : "",
                        p.m3 != null ? p.m3.trendDirection : "",
                        p.m3 != null ? p.m3.bodyPct : 0,
                        p.quarterComplete ? "YES" : "NO",
                        p.actualTrend != null ? p.actualTrend : "",
                        p.quarterComplete && !p.predictedTrend.equals("UNCERTAIN")
                                ? (p.predictionCorrect ? "YES" : "NO") : "");
            }
        }
        System.out.printf("Written: %-55s (%,d rows)%n", path, preds.size());
    }

    // -- WRITE PRIME SIGNALS (M1+M2 agree, 86.65%+) ---------------------------
    static void writePrimeSignals(List<QuarterPrediction> preds, String path) throws Exception {
        List<QuarterPrediction> prime = preds.stream()
                .filter(p -> p.signalType.equals("M1_M2_AGREE") ||
                        p.signalType.equals("ALL3_AGREE"))
                .collect(Collectors.toList());

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,QUARTER,SIGNAL_TYPE,PREDICTED_TREND,CONFIDENCE_PCT," +
                    "OPTIONS_ACTION,ENTRY_WINDOW," +
                    "M1_TREND,M1_BODY_PCT,M2_TREND,M2_BODY_PCT," +
                    "QUARTER_COMPLETE,ACTUAL_TREND,PREDICTION_CORRECT");
            for (QuarterPrediction p : prime) {
                pw.printf("%s,%s,%s,%s,%.2f,%s,%s," +
                                "%s,%.1f,%s,%.1f," +
                                "%s,%s,%s%n",
                        p.symbol, p.quarterPeriod, p.signalType,
                        p.predictedTrend, p.confidencePct,
                        p.optionsAction(), p.entryWindow,
                        p.m1 != null ? p.m1.trendDirection : "",
                        p.m1 != null ? p.m1.bodyPct : 0,
                        p.m2 != null ? p.m2.trendDirection : "",
                        p.m2 != null ? p.m2.bodyPct : 0,
                        p.quarterComplete ? "YES" : "NO",
                        p.actualTrend != null ? p.actualTrend : "",
                        p.quarterComplete && !p.predictedTrend.equals("UNCERTAIN")
                                ? (p.predictionCorrect ? "YES" : "NO") : "PENDING");
            }
        }
        System.out.printf("Written: %-55s (%,d prime signals)%n", path, prime.size());
    }

    // -- WRITE CURRENT QUARTER REPORT ------------------------------------------
    static void writeCurrentQuarter(List<QuarterPrediction> preds, String path) throws Exception {
        // Find the latest quarter in data
        String latestQ = preds.stream()
                .map(p -> p.quarterPeriod)
                .max(Comparator.naturalOrder())
                .orElse("");

        // Also include second latest (previous quarter -- may have fresh M1+M2 signal)
        List<String> recentQs = preds.stream()
                .map(p -> p.quarterPeriod)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(2)
                .collect(Collectors.toList());

        List<QuarterPrediction> current = preds.stream()
                .filter(p -> recentQs.contains(p.quarterPeriod))
                .filter(p -> !p.predictedTrend.equals("UNCERTAIN"))
                .filter(p -> p.confidencePct >= 64.0)
                .sorted(Comparator.comparingDouble((QuarterPrediction p) -> p.confidencePct).reversed())
                .collect(Collectors.toList());

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,QUARTER,SIGNAL_TYPE,PREDICTED_TREND,CONFIDENCE_PCT," +
                    "OPTIONS_ACTION,ACTION_RECOMMENDED,ENTRY_WINDOW," +
                    "M1_PERIOD,M1_TREND,M1_BODY_PCT,M1_CLOSE_ABOVE_OPEN," +
                    "M2_PERIOD,M2_TREND,M2_BODY_PCT,M2_CLOSE_ABOVE_OPEN," +
                    "M3_PERIOD,M3_TREND");
            for (QuarterPrediction p : current) {
                String m1Period = p.m1 != null ? p.m1.period : "";
                String m2Period = p.m2 != null ? p.m2.period : "NOT YET CLOSED";
                String m3Period = p.m3 != null ? p.m3.period : "NOT YET CLOSED";
                pw.printf("%s,%s,%s,%s,%.2f,%s,%s,%s," +
                                "%s,%s,%.1f,%s," +
                                "%s,%s,%.1f,%s," +
                                "%s,%s%n",
                        p.symbol, p.quarterPeriod, p.signalType,
                        p.predictedTrend, p.confidencePct,
                        p.optionsAction(), p.actionRecommended, p.entryWindow,
                        m1Period,
                        p.m1 != null ? p.m1.trendDirection : "",
                        p.m1 != null ? p.m1.bodyPct : 0,
                        p.m1 != null ? (p.m1.closedAboveOpen ? "YES" : "NO") : "",
                        m2Period,
                        p.m2 != null ? p.m2.trendDirection : "",
                        p.m2 != null ? p.m2.bodyPct : 0,
                        p.m2 != null ? (p.m2.closedAboveOpen ? "YES" : "NO") : "",
                        m3Period,
                        p.m3 != null ? p.m3.trendDirection : "");
            }
        }
        System.out.printf("Written: %-55s (%,d current signals)%n", path, current.size());
    }

    // -- CONSOLE SUMMARY -------------------------------------------------------
    static void printConsoleSummary(List<QuarterPrediction> preds) {
        // Find current/latest quarter
        String latestQ = preds.stream()
                .map(p -> p.quarterPeriod)
                .max(Comparator.naturalOrder())
                .orElse("N/A");

        List<QuarterPrediction> latest = preds.stream()
                .filter(p -> p.quarterPeriod.equals(latestQ))
                .filter(p -> p.confidencePct >= 86.0)
                .sorted(Comparator.comparingDouble((QuarterPrediction p) -> p.confidencePct).reversed())
                .collect(Collectors.toList());

        System.out.println("=== CURRENT QUARTER PRIME SIGNALS -- " + latestQ + " ===");
        System.out.println("(Showing 86%+ confidence only -- FULL POSITION signals)");
        System.out.println();

        long sellCalls = latest.stream().filter(p -> p.predictedTrend.equals("DOWN")).count();
        long sellPuts  = latest.stream().filter(p -> p.predictedTrend.equals("UP")).count();
        System.out.printf("SELL CALLS candidates (DOWN trend): %,d stocks%n", sellCalls);
        System.out.printf("SELL PUTS  candidates (UP trend)  : %,d stocks%n", sellPuts);
        System.out.println();

        System.out.printf("%-15s %-8s %-16s %-7s %8s  %-14s  %s%n",
                "SYMBOL","QUARTER","SIGNAL","TREND","CONF%","OPTIONS","ENTRY WINDOW");
        System.out.println("-".repeat(90));

        int shown = 0;
        for (QuarterPrediction p : latest) {
            if (shown++ >= 30) { System.out.println("  ... see current_quarter_report.csv for all"); break; }
            System.out.printf("%-15s %-8s %-16s %-7s %7.2f%%  %-14s  %s%n",
                    p.symbol, p.quarterPeriod, p.signalType,
                    p.predictedTrend, p.confidencePct,
                    p.optionsAction(), p.entryWindow);
        }

        System.out.println();
        System.out.println("-".repeat(57));
        System.out.println("CONFIDENCE GUIDE:");
        System.out.println("  64-74%  -> WATCHLIST only     (M1 signal fired)");
        System.out.println("  74-79%  -> SMALL position     (M2 signal or M1 large body)");
        System.out.println("  86-87%  -> FULL position      <- PRIME ENTRY (M1+M2 agree)");
        System.out.println("  96%     -> ADD to position    (all 3 months agree)");
        System.out.println();
        System.out.println("OPTIONS ACTION:");
        System.out.println("  Predicted DOWN -> SELL CALLS (price unlikely to rise)");
        System.out.println("  Predicted UP   -> SELL PUTS  (price unlikely to fall)");
    }

    // -- HELPERS ---------------------------------------------------------------
    static double safeDouble(String s) {
        try { return Double.parseDouble(s.trim().replace(",", "")); }
        catch (Exception e) { return 0.0; }
    }

    static long safeLong(String s) {
        try {
            String t = s.trim().replace(",", "");
            if (t.contains(".")) t = t.split("\\.")[0];
            return Long.parseLong(t);
        } catch (Exception e) { return 0L; }
    }

    static void printBanner() {
        System.out.println("+==============================================================+");
        System.out.println("|         Early Quarter Predictor -- NSE Options Selling        |");
        System.out.println("|  M1 alone=64%  M2 alone=74%  M1+M2=86%  All3=96%            |");
        System.out.println("+==============================================================+");
        System.out.println();
    }
}