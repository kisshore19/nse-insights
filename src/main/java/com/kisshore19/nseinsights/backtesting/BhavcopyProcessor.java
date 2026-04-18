import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

/**
 * NSE Bhavcopy Processor
 * ─────────────────────────────────────────────────────────────
 * INPUT  : Folder of NSE equity bhavcopy CSV files (one per day)
 * FORMAT : SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE,
 *          LOW_PRICE, LAST_PRICE(used as CLOSE), CLOSE_PRICE, AVG_PRICE,
 *          TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
 *
 * NOTE   : LAST_PRICE (col 7) is used as the closing price as instructed.
 *
 * OUTPUT : monthly_ohlc.csv   — monthly OHLC candles per stock
 *          quarterly_ohlc.csv — quarterly OHLC candles per stock
 *
 * TREND LOGIC (your observation):
 *   High formed before Low  → DOWNTREND → close below open
 *   Low formed before High  → UPTREND   → close above open
 * ─────────────────────────────────────────────────────────────
 * USAGE:
 *   javac BhavcopyProcessor.java
 *   java BhavcopyProcessor <bhavcopy_folder> <output_folder> [SYMBOL]
 *
 * EXAMPLES:
 *   java BhavcopyProcessor ./data ./output
 *   java BhavcopyProcessor ./data ./output RELIANCE
 */
public class BhavcopyProcessor {

    // ── COLUMN INDICES (0-based after trim) ─────────────────────────────────
    static final int COL_SYMBOL    = 0;
    static final int COL_SERIES    = 1;
    static final int COL_DATE      = 2;
    static final int COL_PREV_CL   = 3;
    static final int COL_OPEN      = 4;
    static final int COL_HIGH      = 5;
    static final int COL_LOW       = 6;
    static final int COL_CLOSE     = 7;  // LAST_PRICE used as close (col 7)
    static final int COL_CLOSE_RAW = 8;  // CLOSE_PRICE col 8 — not used
    static final int COL_AVG       = 9;
    static final int COL_VOLUME    = 10;
    static final int COL_TURNOVER  = 11;
    static final int COL_TRADES    = 12;
    static final int COL_DELIV_QTY = 13;
    static final int COL_DELIV_PER = 14;

    static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);

    // ── DAILY RECORD ─────────────────────────────────────────────────────────
    static class DailyRecord {
        String    symbol;
        LocalDate date;
        double    open, high, low, close, prevClose;
        long      volume;
        long      delivQty;
        double    delivPer;

        DailyRecord(String symbol, LocalDate date,
                    double open, double high, double low, double close,
                    double prevClose, long volume, long delivQty, double delivPer) {
            this.symbol    = symbol;
            this.date      = date;
            this.open      = open;
            this.high      = high;
            this.low       = low;
            this.close     = close;
            this.prevClose = prevClose;
            this.volume    = volume;
            this.delivQty  = delivQty;
            this.delivPer  = delivPer;
        }
    }

    // ── OHLC CANDLE ──────────────────────────────────────────────────────────
    static class OHLCCandle {
        String    symbol;
        String    period;
        LocalDate periodStart;
        LocalDate periodEnd;
        int       tradingDays;

        double open, high, low, close;
        long   totalVolume;
        long   totalDelivQty;
        double avgDelivPer;

        LocalDate highDate;
        LocalDate lowDate;
        String    trendDirection;
        boolean   closedAboveOpen;
        boolean   trendMatchesBody;

        double bodySize()  { return Math.abs(close - open); }
        double fullRange() { return high - low; }
        double bodyPct()   { return fullRange() > 0 ? bodySize() / fullRange() * 100.0 : 0; }
        double delivPct()  { return totalVolume > 0 ? (double) totalDelivQty / totalVolume * 100.0 : 0; }
        double changeAmt() { return close - open; }
        double changePct() { return open > 0 ? (close - open) / open * 100.0 : 0; }
    }

    // ── MAIN ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        String inputDir     = args.length > 0 ? args[0] : "E:\\StockResearch\\Data\\NseData\\Bhav-Data";
        String outputDir    = args.length > 1 ? args[1] : "./output";
        String filterSymbol = args.length > 2 ? args[2].toUpperCase().trim() : "";

        printBanner();
        System.out.println("Input folder : " + inputDir);
        System.out.println("Output folder: " + outputDir);
        System.out.println("Filter symbol: " + (filterSymbol.isEmpty() ? "ALL STOCKS" : filterSymbol));
        System.out.println();

        // 1. Load
        List<DailyRecord> allRecords = loadAll(inputDir, filterSymbol);
        if (allRecords.isEmpty()) {
            System.out.println("No records loaded. Check folder path and CSV format.");
            return;
        }

        long uniqueSymbols = allRecords.stream().map(r -> r.symbol).distinct().count();
        System.out.printf("Loaded %,d daily records  |  %,d unique symbols%n%n",
                allRecords.size(), uniqueSymbols);

        // 2. Group by symbol, sorted by date
        Map<String, List<DailyRecord>> bySymbol = allRecords.stream()
                .sorted(Comparator.comparing((DailyRecord r) -> r.symbol)
                        .thenComparing(r -> r.date))
                .collect(Collectors.groupingBy(
                        r -> r.symbol,
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 3. Build candles
        List<OHLCCandle> monthly   = new ArrayList<>();
        List<OHLCCandle> quarterly = new ArrayList<>();

        for (Map.Entry<String, List<DailyRecord>> entry : bySymbol.entrySet()) {
            monthly.addAll(buildMonthly(entry.getKey(), entry.getValue()));
            quarterly.addAll(buildQuarterly(entry.getKey(), entry.getValue()));
        }

        System.out.printf("Monthly candles  : %,d%n", monthly.size());
        System.out.printf("Quarterly candles: %,d%n%n", quarterly.size());

        // 4. Write CSVs
        Files.createDirectories(Paths.get(outputDir));
        writeCSV(monthly,   outputDir + "/monthly_ohlc.csv");
        writeCSV(quarterly, outputDir + "/quarterly_ohlc.csv");

        // 5. Console summary
        printConsoleSummary(quarterly);

        System.out.println();
        System.out.println("Done. Files written to: " + outputDir);
        System.out.println("  monthly_ohlc.csv");
        System.out.println("  quarterly_ohlc.csv");
    }

    // ── LOAD ALL BHAVCOPY FILES ───────────────────────────────────────────────
    static List<DailyRecord> loadAll(String folderPath, String filterSymbol) {
        List<DailyRecord> records = new ArrayList<>();
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Folder not found: " + folderPath);
            System.out.println("Generating sample data for demo...");
            try {
                generateSampleData(folderPath);
                folder = new File(folderPath);
            } catch (Exception e) {
                System.out.println("Could not generate sample: " + e.getMessage());
                return records;
            }
        }

        File[] files = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".csv"));
        if (files == null || files.length == 0) {
            System.out.println("No CSV files found in: " + folderPath);
            return records;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        System.out.printf("Found %d CSV file(s). Parsing...%n", files.length);

        int done = 0;
        for (File f : files) {
            records.addAll(parseFile(f, filterSymbol));
            done++;
            if (done % 100 == 0)
                System.out.printf("  Parsed %d / %d files...%n", done, files.length);
        }
        System.out.printf("  Parsed all %d files.%n", done);
        return records;
    }

    // ── PARSE ONE BHAVCOPY CSV ────────────────────────────────────────────────
    static List<DailyRecord> parseFile(File file, String filterSymbol) {
        List<DailyRecord> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header
                if (firstLine) {
                    firstLine = false;
                    if (line.toUpperCase().contains("SYMBOL")) continue;
                }

                String[] cols = line.split(",");
                for (int i = 0; i < cols.length; i++) cols[i] = cols[i].trim();

                if (cols.length < 13) continue;

                try {
                    String symbol = cols[COL_SYMBOL].toUpperCase();
                    String series = cols[COL_SERIES].toUpperCase();

                    if (!series.equals("EQ") && !series.equals("BE") && !series.equals("BZ"))
                        continue;

                    if (!filterSymbol.isEmpty() && !symbol.equals(filterSymbol))
                        continue;

                    LocalDate date     = LocalDate.parse(cols[COL_DATE].trim(), DATE_FMT);
                    double    prevCl   = safeDouble(cols, COL_PREV_CL);
                    double    open     = safeDouble(cols, COL_OPEN);
                    double    high     = safeDouble(cols, COL_HIGH);
                    double    low      = safeDouble(cols, COL_LOW);
                    double    close    = safeDouble(cols, COL_CLOSE); // LAST_PRICE col 7
                    long      volume   = safeLong(cols, COL_VOLUME);
                    long      delivQty = cols.length > COL_DELIV_QTY ? safeLong(cols, COL_DELIV_QTY)   : 0L;
                    double    delivPer = cols.length > COL_DELIV_PER ? safeDouble(cols, COL_DELIV_PER) : 0.0;

                    if (open <= 0 || high <= 0 || low <= 0 || close <= 0) continue;
                    if (high < low) continue;

                    records.add(new DailyRecord(symbol, date,
                            open, high, low, close, prevCl,
                            volume, delivQty, delivPer));

                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("  Warning: " + file.getName() + " — " + e.getMessage());
        }
        return records;
    }

    // ── BUILD MONTHLY CANDLES ─────────────────────────────────────────────────
    static List<OHLCCandle> buildMonthly(String symbol, List<DailyRecord> days) {
        Map<String, List<DailyRecord>> groups = new LinkedHashMap<>();
        for (DailyRecord d : days) {
            String key = String.format("%d-%02d", d.date.getYear(), d.date.getMonthValue());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<OHLCCandle> out = new ArrayList<>();
        for (Map.Entry<String, List<DailyRecord>> e : groups.entrySet())
            out.add(aggregate(symbol, e.getKey(), e.getValue()));
        return out;
    }

    // ── BUILD QUARTERLY CANDLES ───────────────────────────────────────────────
    static List<OHLCCandle> buildQuarterly(String symbol, List<DailyRecord> days) {
        Map<String, List<DailyRecord>> groups = new LinkedHashMap<>();
        for (DailyRecord d : days) {
            int q = (d.date.getMonthValue() - 1) / 3 + 1;
            String key = d.date.getYear() + "-Q" + q;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<OHLCCandle> out = new ArrayList<>();
        for (Map.Entry<String, List<DailyRecord>> e : groups.entrySet())
            out.add(aggregate(symbol, e.getKey(), e.getValue()));
        return out;
    }

    // ── AGGREGATE DAYS → ONE CANDLE ───────────────────────────────────────────
    static OHLCCandle aggregate(String symbol, String period, List<DailyRecord> days) {
        OHLCCandle c    = new OHLCCandle();
        c.symbol        = symbol;
        c.period        = period;
        c.periodStart   = days.get(0).date;
        c.periodEnd     = days.get(days.size() - 1).date;
        c.tradingDays   = days.size();
        c.open          = days.get(0).open;
        c.close         = days.get(days.size() - 1).close;
        c.high          = Double.MIN_VALUE;
        c.low           = Double.MAX_VALUE;
        c.totalVolume   = 0;
        c.totalDelivQty = 0;
        double delivSum = 0;

        LocalDate firstHighDate = null;
        LocalDate firstLowDate  = null;

        for (DailyRecord d : days) {
            if (d.high > c.high) { c.high = d.high; firstHighDate = d.date; }
            if (d.low  < c.low)  { c.low  = d.low;  firstLowDate  = d.date; }
            c.totalVolume   += d.volume;
            c.totalDelivQty += d.delivQty;
            delivSum        += d.delivPer;
        }

        c.avgDelivPer = delivSum / days.size();
        c.highDate    = firstHighDate;
        c.lowDate     = firstLowDate;

        // ── YOUR CORE TREND RULE ──────────────────────────────────────────────
        // High forms first then low  →  DOWN trend  →  close < open
        // Low forms first then high  →  UP trend    →  close > open
        if (firstHighDate != null && firstLowDate != null) {
            if      (firstHighDate.isBefore(firstLowDate)) c.trendDirection = "DOWN";
            else if (firstLowDate.isBefore(firstHighDate)) c.trendDirection = "UP";
            else                                           c.trendDirection = "FLAT";
        } else {
            c.trendDirection = "FLAT";
        }

        c.closedAboveOpen  = c.close > c.open;

        // Validate: does trend direction match candle body?
        c.trendMatchesBody =
                (c.trendDirection.equals("UP")   &&  c.closedAboveOpen) ||
                        (c.trendDirection.equals("DOWN")  && !c.closedAboveOpen) ||
                        c.trendDirection.equals("FLAT");

        return c;
    }

    // ── WRITE CSV ─────────────────────────────────────────────────────────────
    static void writeCSV(List<OHLCCandle> candles, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("SYMBOL,PERIOD,PERIOD_START,PERIOD_END,TRADING_DAYS," +
                    "OPEN,HIGH,LOW,CLOSE,CHANGE_AMT,CHANGE_PCT," +
                    "BODY_SIZE,BODY_PCT,FULL_RANGE," +
                    "CLOSE_ABOVE_OPEN,TREND_DIRECTION,TREND_MATCHES_BODY," +
                    "HIGH_DATE,LOW_DATE," +
                    "TOTAL_VOLUME,TOTAL_DELIV_QTY,DELIV_PCT,AVG_DELIV_PER");

            for (OHLCCandle c : candles) {
                pw.printf("%s,%s,%s,%s,%d," +
                                "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f," +
                                "%.2f,%.2f,%.2f," +
                                "%s,%s,%s," +
                                "%s,%s," +
                                "%d,%d,%.2f,%.2f%n",
                        c.symbol, c.period, c.periodStart, c.periodEnd, c.tradingDays,
                        c.open, c.high, c.low, c.close, c.changeAmt(), c.changePct(),
                        c.bodySize(), c.bodyPct(), c.fullRange(),
                        c.closedAboveOpen ? "YES" : "NO",
                        c.trendDirection,
                        c.trendMatchesBody ? "YES" : "NO",
                        c.highDate, c.lowDate,
                        c.totalVolume, c.totalDelivQty, c.delivPct(), c.avgDelivPer);
            }
        }
        System.out.printf("Written: %-50s  (%,d rows)%n", path, candles.size());
    }

    // ── CONSOLE SUMMARY ───────────────────────────────────────────────────────
    static void printConsoleSummary(List<OHLCCandle> quarterly) {
        System.out.println();
        System.out.println("──────────────────────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-15s %-8s %8s %8s %8s %8s  %-5s  %-3s  %7s  %7s  %s%n",
                "SYMBOL","PERIOD","OPEN","HIGH","LOW","CLOSE","TREND","C>O","DELIV%","BODY%","MATCH?");
        System.out.println("──────────────────────────────────────────────────────────────────────────────────────────");

        Map<String, List<OHLCCandle>> bySymbol = quarterly.stream()
                .collect(Collectors.groupingBy(c -> c.symbol, LinkedHashMap::new, Collectors.toList()));

        int symCount = 0;
        for (Map.Entry<String, List<OHLCCandle>> e : bySymbol.entrySet()) {
            if (++symCount > 8) {
                System.out.println("  ... (see quarterly_ohlc.csv for all symbols)");
                break;
            }
            List<OHLCCandle> qs = e.getValue();
            int from = Math.max(0, qs.size() - 4);
            for (int i = from; i < qs.size(); i++) {
                OHLCCandle c = qs.get(i);
                System.out.printf("%-15s %-8s %8.2f %8.2f %8.2f %8.2f  %-5s  %-3s  %6.1f%%  %6.1f%%  %s%n",
                        c.symbol, c.period,
                        c.open, c.high, c.low, c.close,
                        c.trendDirection,
                        c.closedAboveOpen ? "YES" : "NO",
                        c.avgDelivPer,
                        c.bodyPct(),
                        c.trendMatchesBody ? "YES" : "NO ← exception");
            }
            System.out.println();
        }

        // Stats
        long total   = quarterly.size();
        long matched = quarterly.stream().filter(c -> c.trendMatchesBody).count();
        long up      = quarterly.stream().filter(c -> c.trendDirection.equals("UP")).count();
        long down    = quarterly.stream().filter(c -> c.trendDirection.equals("DOWN")).count();

        System.out.println("──────────────────────────────────────────────────────────────────────────────────────────");
        System.out.printf("Total quarterly candles         : %,d%n", total);
        System.out.printf("Uptrend   (low first, UP)       : %,d%n", up);
        System.out.printf("Downtrend (high first, DOWN)    : %,d%n", down);
        System.out.printf("Trend matches body direction    : %,d / %,d  (%.1f%%)%n",
                matched, total, total > 0 ? matched * 100.0 / total : 0);
        System.out.println();
        System.out.println("TREND_MATCHES_BODY = NO means high/low timing and close/open direction disagree.");
        System.out.println("These are the exception cases worth studying separately.");
    }

    // ── GENERATE SAMPLE BHAVCOPY DATA ────────────────────────────────────────
    static void generateSampleData(String folderPath) throws Exception {
        Files.createDirectories(Paths.get(folderPath));
        String[] symbols = {"RELIANCE","TCS","INFY","HDFCBANK","SBIN","MARUTI","WIPRO","AXISBANK"};
        Random rand = new Random(42);
        Map<String, Double> prices = new HashMap<>();
        for (String s : symbols) prices.put(s, 200 + rand.nextDouble() * 3000);

        LocalDate start = LocalDate.of(2022, 1, 3);
        LocalDate end   = LocalDate.of(2024, 12, 31);
        Map<LocalDate, List<String[]>> byDate = new TreeMap<>();

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                List<String[]> rows = new ArrayList<>();
                for (String sym : symbols) {
                    double price = prices.get(sym);
                    double chg   = price * (rand.nextGaussian() * 0.016);
                    double open  = Math.max(1, price + chg * 0.4);
                    double hi    = open + Math.abs(rand.nextGaussian() * price * 0.01);
                    double lo    = open - Math.abs(rand.nextGaussian() * price * 0.01);
                    double close = Math.max(lo, Math.min(hi, open + chg));
                    long   vol   = 50000 + (long)(rand.nextDouble() * 2000000);
                    long   delQ  = (long)(vol * (0.25 + rand.nextDouble() * 0.55));
                    double delP  = delQ * 100.0 / vol;
                    prices.put(sym, close);

                    String ds = cur.format(DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH));
                    rows.add(new String[]{
                            sym, "EQ", ds,
                            String.format("%.2f", price),
                            String.format("%.2f", open),
                            String.format("%.2f", hi),
                            String.format("%.2f", lo),
                            String.format("%.2f", close),
                            String.format("%.2f", close),
                            String.format("%.2f", (open+hi+lo+close)/4),
                            String.valueOf(vol),
                            String.format("%.2f", vol*close/100000),
                            String.valueOf(1000+rand.nextInt(9000)),
                            String.valueOf(delQ),
                            String.format("%.2f", delP)
                    });
                }
                byDate.put(cur, rows);
            }
            cur = cur.plusDays(1);
        }

        String hdr = "SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER";
        for (Map.Entry<LocalDate, List<String[]>> e : byDate.entrySet()) {
            String fn = folderPath + "/bhav_" +
                    e.getKey().format(DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH)).toUpperCase() + ".csv";
            try (PrintWriter pw = new PrintWriter(new FileWriter(fn))) {
                pw.println(hdr);
                for (String[] row : e.getValue())
                    pw.println(String.join(", ", row));
            }
        }
        System.out.printf("Generated %d sample bhavcopy files in: %s%n%n", byDate.size(), folderPath);
    }

    // ── SAFE PARSERS ──────────────────────────────────────────────────────────
    static double safeDouble(String[] cols, int idx) {
        try { return Double.parseDouble(cols[idx].trim().replace(",", "")); }
        catch (Exception e) { return 0.0; }
    }

    static long safeLong(String[] cols, int idx) {
        try {
            String s = cols[idx].trim().replace(",", "");
            if (s.contains(".")) s = s.split("\\.")[0];
            return Long.parseLong(s);
        } catch (Exception e) { return 0L; }
    }

    static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║    NSE Bhavcopy  →  Monthly & Quarterly OHLC        ║");
        System.out.println("║    Trend: High first = DOWN | Low first = UP         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}