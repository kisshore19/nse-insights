POST /api/v1/candles/stats/build

Params:
timeframe  — YEAR | QUARTER | MONTH | WEEK  (required)
period     — single period e.g. "2024", "2024-Q1", "2024-01", "2024-W03"  (optional)
from       — range start  e.g. "2024", "2024-Q1", "2024-01", "2024-W03"  (optional)
to         — range end    e.g. "2024", "2024-Q3", "2024-06", "2024-W12"  (optional)

Rules:
- period alone     → build stats for that single period only
- from + to        → build stats for all periods in that range (inclusive)
- none of the above → build stats for ALL periods (full build, same as before)
- period + from/to → 400 Bad Request (ambiguous)

Behavior per period:
1. Check if candle_stats already has a row for (symbol, timeframe, period_key)
2. If YES → skip, do not touch it
3. If NO  → compute from nse_daily_price and insert
4. Batch save all new records for that period at once

Output:
{
timeframe   : "MONTH",
periodsFound    : 12,     // how many periods were in the requested range
periodsSkipped  : 9,      // already had stats — not touched
periodsInserted : 3,      // newly computed and saved
recordsInserted : 7200    // total rows saved (periodsInserted × ~2400 symbols)
}