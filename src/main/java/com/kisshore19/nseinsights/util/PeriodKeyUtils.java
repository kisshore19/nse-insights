package com.kisshore19.nseinsights.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting period keys to date ranges and generating
 * period key sequences.
 *
 * Period key formats:
 *   YEAR    → "2024"
 *   QUARTER → "2024-Q1"
 *   MONTH   → "2024-01"
 *   WEEK    → "2024-W03"  (ISO week, Monday start)
 */
public final class PeriodKeyUtils {

    private PeriodKeyUtils() {}

    // ── Date range from period key ─────────────────────────────────────────────

    /**
     * Returns the first calendar day of the period.
     * Used as the :fromDate param in the stats query.
     */
    public static LocalDate periodStart(String timeframe, String periodKey) {
        return switch (timeframe) {
            case "YEAR"    -> yearStart(periodKey);
            case "QUARTER" -> quarterStart(periodKey);
            case "MONTH"   -> monthStart(periodKey);
            case "WEEK"    -> weekStart(periodKey);
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }

    /**
     * Returns the last calendar day of the period.
     * Used as the :toDate param in the stats query.
     */
    public static LocalDate periodEnd(String timeframe, String periodKey) {
        return switch (timeframe) {
            case "YEAR"    -> yearEnd(periodKey);
            case "QUARTER" -> quarterEnd(periodKey);
            case "MONTH"   -> monthEnd(periodKey);
            case "WEEK"    -> weekEnd(periodKey);
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }

    // ── Generate all period keys between from and to (inclusive) ──────────────

    /**
     * Generates all period keys between from and to (inclusive, string order).
     * Used when the caller passes from + to range params.
     */
    public static List<String> periodKeysBetween(
            String timeframe, String from, String to) {

        List<String> all = new ArrayList<>();
        LocalDate    cursor = periodStart(timeframe, from);
        LocalDate    end    = periodEnd(timeframe, to);

        while (!cursor.isAfter(end)) {
            all.add(toPeriodKey(timeframe, cursor));
            cursor = nextPeriodStart(timeframe, cursor);
        }
        return all;
    }

    // ── Convert a date to its period key ──────────────────────────────────────

    public static String toPeriodKey(String timeframe, LocalDate date) {
        return switch (timeframe) {
            case "YEAR"    -> String.valueOf(date.getYear());
            case "QUARTER" -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "MONTH"   -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case "WEEK"    -> {
                // Use ISO week — week containing Thursday belongs to that year
                int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
                int year = date.get(WeekFields.ISO.weekBasedYear());
                yield String.format("%04d-W%02d", year, week);
            }
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public static void validatePeriodKey(String timeframe, String periodKey) {
        try {
            periodStart(timeframe, periodKey); // will throw if format is wrong
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid period key '%s' for timeframe %s. Expected format: %s"
                            .formatted(periodKey, timeframe, exampleFormat(timeframe)));
        }
    }

    public static String exampleFormat(String timeframe) {
        return switch (timeframe) {
            case "YEAR"    -> "2024";
            case "QUARTER" -> "2024-Q1";
            case "MONTH"   -> "2024-01";
            case "WEEK"    -> "2024-W03";
            default        -> "unknown";
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static LocalDate yearStart(String key) {
        // "2024" → 2024-01-01
        int year = Integer.parseInt(key.trim());
        return LocalDate.of(year, 1, 1);
    }

    private static LocalDate yearEnd(String key) {
        // "2024" → 2024-12-31
        int year = Integer.parseInt(key.trim());
        return LocalDate.of(year, 12, 31);
    }

    private static LocalDate quarterStart(String key) {
        // "2024-Q1" → 2024-01-01
        String[] parts   = key.split("-Q");
        int year         = Integer.parseInt(parts[0]);
        int quarter      = Integer.parseInt(parts[1]);
        int startMonth   = (quarter - 1) * 3 + 1;
        return LocalDate.of(year, startMonth, 1);
    }

    private static LocalDate quarterEnd(String key) {
        // "2024-Q1" → 2024-03-31
        String[] parts = key.split("-Q");
        int year       = Integer.parseInt(parts[0]);
        int quarter    = Integer.parseInt(parts[1]);
        int endMonth   = quarter * 3;
        return LocalDate.of(year, endMonth, 1)
                        .with(TemporalAdjusters.lastDayOfMonth());
    }

    private static LocalDate monthStart(String key) {
        // "2024-01" → 2024-01-01
        String[] parts = key.split("-");
        int year  = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        return LocalDate.of(year, month, 1);
    }

    private static LocalDate monthEnd(String key) {
        // "2024-01" → 2024-01-31
        return monthStart(key).with(TemporalAdjusters.lastDayOfMonth());
    }

    private static LocalDate weekStart(String key) {
        // "2024-W03" → Monday of ISO week 3 of 2024
        String[] parts = key.split("-W");
        int year = Integer.parseInt(parts[0]);
        int week = Integer.parseInt(parts[1]);
        return LocalDate.of(year, 1, 4)  // Jan 4 is always in week 1
                .with(WeekFields.ISO.weekBasedYear(), year)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate weekEnd(String key) {
        // "2024-W03" → Sunday of ISO week 3 of 2024
        return weekStart(key).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    private static LocalDate nextPeriodStart(String timeframe, LocalDate current) {
        return switch (timeframe) {
            case "YEAR"    -> current.plusYears(1);
            case "QUARTER" -> current.plusMonths(3);
            case "MONTH"   -> current.plusMonths(1);
            case "WEEK"    -> current.plusWeeks(1);
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }
}