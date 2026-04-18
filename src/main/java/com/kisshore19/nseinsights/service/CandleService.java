package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.entity.StockCandle;
import com.kisshore19.nseinsights.repository.CandleProjection;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import com.kisshore19.nseinsights.repository.StockCandleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleService {

    private final NseDailyPriceRepository priceRepository;
    private final StockCandleRepository   candleRepository;
    private final CandleStatsService      candleStatsService;

    private static final List<String> ALL_TIMEFRAMES = List.of(
            StockCandle.TF_DAY, StockCandle.TF_WEEK,
            StockCandle.TF_MONTH, StockCandle.TF_QUARTER, StockCandle.TF_YEAR);

    // ── Build all symbols for a timeframe ─────────────────────────────────────
    // Routes to the dedicated per-timeframe method for clean Hibernate SQL logs
    @Transactional
    public int buildCandles(String timeframe) {
        log.info("Building {} candles for all symbols...", timeframe);
        candleRepository.deleteByTimeframe(timeframe);

        List<CandleProjection> projections = buildAllSymbolProjections(timeframe);
        log.info("DB returned {} {} candle projections", projections.size(), timeframe);

        List<StockCandle> candles = projections.stream().map(this::toEntity).toList();
        candleRepository.saveAll(candles);
        log.info("Saved {} {} candles", candles.size(), timeframe);
        // Auto-trigger stats build after candles are saved
        if (StockCandle.TF_YEAR.equals(timeframe)) {
            log.info("Auto-triggering stats build for timeframe={}", timeframe);
//            candleStatsService.buildStats(timeframe);
        }
        return candles.size();
    }

    // ── Build candles for a single symbol ─────────────────────────────────────
    // Routes to the dedicated per-timeframe method for clean Hibernate SQL logs
    @Transactional
    public int buildCandlesForSymbol(String symbol, String timeframe) {
        log.info("Building {} candles for symbol {}...", timeframe, symbol);
        candleRepository.deleteBySymbolAndTimeframe(symbol, timeframe);

        List<CandleProjection> projections = buildSymbolProjections(symbol, timeframe);

        List<StockCandle> candles = projections.stream().map(this::toEntity).toList();
        candleRepository.saveAll(candles);
        log.info("Saved {} {} candles for {}", candles.size(), timeframe, symbol);
        return candles.size();
    }

    // ── Route: all symbols → per-timeframe query ───────────────────────────────
    private List<CandleProjection> buildAllSymbolProjections(String timeframe) {
        return switch (timeframe) {
            case StockCandle.TF_DAY     -> priceRepository.buildAllDayCandles();
            case StockCandle.TF_WEEK    -> priceRepository.buildAllWeekCandles();
            case StockCandle.TF_MONTH   -> priceRepository.buildAllMonthCandles();
            case StockCandle.TF_QUARTER -> priceRepository.buildAllQuarterCandles();
            case StockCandle.TF_YEAR    -> priceRepository.buildAllYearCandles();
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }

    // ── Route: single symbol → per-timeframe query ────────────────────────────
    private List<CandleProjection> buildSymbolProjections(String symbol, String timeframe) {
        LocalDate from = LocalDate.of(2000, 1, 1);
        LocalDate to   = LocalDate.of(2099, 12, 31);
        return switch (timeframe) {
            case StockCandle.TF_DAY     -> priceRepository.buildDayCandles(symbol, from, to);
//            case StockCandle.TF_WEEK    -> priceRepository.buildWeekCandles(symbol, from, to);
//            case StockCandle.TF_MONTH   -> priceRepository.buildMonthCandles(symbol, from, to);
            case StockCandle.TF_QUARTER -> priceRepository.buildQuarterCandles(symbol, from, to);
            case StockCandle.TF_YEAR    -> priceRepository.buildYearCandles(symbol, from, to);
            default -> throw new IllegalArgumentException("Unknown timeframe: " + timeframe);
        };
    }

    // ── Map DB projection → StockCandle entity ────────────────────────────────
    private StockCandle toEntity(CandleProjection p) {
        return StockCandle.builder()
                .symbol(p.getSymbol())
                .timeframe(p.getTimeframe())
                .candleDate(p.getCandleDate())
                .candleEndDate(p.getCandleEndDate())
                .openPrice(p.getOpenPrice())
                .highPrice(p.getHighPrice())
                .lowPrice(p.getLowPrice())
                .closePrice(p.getClosePrice())
                .isGreen(p.getIsGreen() != null && p.getIsGreen() == 1)
                .trend(p.getTrend())
                .highDate(p.getHighDate())
                .lowDate(p.getLowDate())
                .totalVolume(p.getTotalVolume())
                .maxVolumeDate(p.getMaxVolumeDate())
                .maxVolumeDayQty(p.getMaxVolumeDayQty())
                .totalDelivery(p.getTotalDelivery())
                .maxDeliveryDate(p.getMaxDeliveryDate())
                .maxDeliveryDayQty(p.getMaxDeliveryDayQty())
                .tradingDays(p.getTradingDays())
                .build();
    }
}