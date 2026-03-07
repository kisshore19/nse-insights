package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.exception.DateNotFoundException;
import com.kisshore19.nseinsights.exception.InvalidDateException;
import com.kisshore19.nseinsights.repository.IndexMasterRepository;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for Module 2 - Data Explorer APIs.
 * Handles stock search, filtering, and analytics queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExplorerService {

    private final NseDailyPriceRepository priceRepository;
    private final IndexMasterRepository indexMasterRepository;

    // Date format: dd-MM-yyyy (e.g., 15-04-1985)
    private static final DateTimeFormatter USER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Parse user input date format (dd-MM-yyyy) to LocalDate
     */
    private LocalDate parseUserDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), USER_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            log.error("Invalid date format: {}. Expected format: dd-MM-yyyy", dateStr);
            throw new InvalidDateException("Invalid date format: " + dateStr + ". Please use dd-MM-yyyy (e.g., 15-04-1985)");
        }
    }

    /**
     * API 1: Advanced stock search with filters
     */
    public StockSearchResponse searchStocks(
            String dateStr,
            String symbol,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long minVolume,
            BigDecimal minPctChange,
            BigDecimal maxPctChange,
            BigDecimal minDeliveryPct,
            Pageable pageable) {

        log.info("Searching stocks with filters - date: {}, symbol: {}, pageable: {}",
                 dateStr, symbol, pageable);

        // Parse date string in dd-MM-yyyy format
        LocalDate tradeDate = parseUserDate(dateStr);

        // If no date provided, use latest available date
        LocalDate searchDate = tradeDate;
        if (searchDate == null) {
            searchDate = priceRepository.findLatestTradeDate()
                    .orElseThrow(() -> new DateNotFoundException("No trading data available in database"));
        }

        Page<NseDailyPrice> results = priceRepository.searchStocks(
                searchDate,
                symbol,
                minPrice,
                maxPrice,
                minVolume,
                minPctChange,
                maxPctChange,
                minDeliveryPct,
                pageable
        );

        List<StockDto> stockDtos = results.getContent().stream()
                .map(this::mapToStockDto)
                .collect(Collectors.toList());

        return StockSearchResponse.builder()
                .content(stockDtos)
                .pageNumber(results.getNumber())
                .pageSize(results.getSize())
                .totalElements(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .isFirst(results.isFirst())
                .isLast(results.isLast())
                .asOfDate(searchDate)
                .resultCount(stockDtos.size())
                .build();
    }

    /**
     * API 2: Get all available trading dates
     */
    public AvailableDatesResponse getAvailableDates() {
        log.info("Fetching all available trading dates");

        List<LocalDate> dates = priceRepository.findAllDistinctTradeDates();

        if (dates.isEmpty()) {
            throw new DateNotFoundException("No trading dates found in database");
        }

        LocalDate latestDate = dates.get(0);  // First element is latest (ordered DESC)
        LocalDate oldestDate = dates.get(dates.size() - 1);

        return AvailableDatesResponse.builder()
                .dates(dates)
                .totalDates(dates.size())
                .latestDate(latestDate)
                .oldestDate(oldestDate)
                .build();
    }

    /**
     * API 3: Get single stock detail for a specific date
     */
    public StockDetailResponse getStockDetail(String symbol, String dateStr) {
        log.info("Fetching stock detail for symbol: {} on date: {}", symbol, dateStr);

        // Parse date string in dd-MM-yyyy format
        LocalDate tradeDate = parseUserDate(dateStr);

        // If no date provided, use latest available date
        final LocalDate searchDate;
        if (tradeDate == null) {
            searchDate = priceRepository.findLatestTradeDate()
                    .orElseThrow(() -> new DateNotFoundException("No trading data available in database"));
        } else {
            searchDate = tradeDate;
        }

        NseDailyPrice price = priceRepository.findByTradeDateAndSymbol(searchDate, symbol.toUpperCase())
                .orElseThrow(() -> new DateNotFoundException(
                        "Stock '" + symbol + "' not found for date: " + searchDate));

        return mapToStockDetailDto(price);
    }

    /**
     * API 4: Get all distinct sectors
     */
    public SectorsResponse getSectors() {
        log.info("Fetching all distinct sectors");

        List<String> sectors = indexMasterRepository.findAllDistinctActiveSectors();

        if (sectors.isEmpty()) {
            log.warn("No sectors found in index_master");
            sectors = List.of();  // Return empty list instead of throwing
        }

        return SectorsResponse.builder()
                .sectors(sectors)
                .totalSectors(sectors.size())
                .build();
    }

    /**
     * API 5: Get top gainers for a date
     */
    public TopMoversResponse getTopGainers(String dateStr, int limit) {
        log.info("Fetching top {} gainers for date: {}", limit, dateStr);

        // Parse date string in dd-MM-yyyy format
        LocalDate tradeDate = parseUserDate(dateStr);

        // If no date provided, use latest available date
        LocalDate searchDate = tradeDate;
        if (searchDate == null) {
            searchDate = priceRepository.findLatestTradeDate()
                    .orElseThrow(() -> new DateNotFoundException("No trading data available in database"));
        }

        // Validate date exists
        if (!priceRepository.existsByTradeDate(searchDate)) {
            throw new DateNotFoundException("No data found for date: " + searchDate);
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "pctChange"));
        List<NseDailyPrice> gainers = priceRepository.findTopGainers(searchDate, pageable);

        List<StockDto> stockDtos = gainers.stream()
                .map(this::mapToStockDto)
                .collect(Collectors.toList());

        return TopMoversResponse.builder()
                .stocks(stockDtos)
                .tradeDate(searchDate)
                .category("GAINERS")
                .count(stockDtos.size())
                .build();
    }

    /**
     * API 6: Get top losers for a date
     */
    public TopMoversResponse getTopLosers(String dateStr, int limit) {
        log.info("Fetching top {} losers for date: {}", limit, dateStr);

        // Parse date string in dd-MM-yyyy format
        LocalDate tradeDate = parseUserDate(dateStr);

        // If no date provided, use latest available date
        LocalDate searchDate = tradeDate;
        if (searchDate == null) {
            searchDate = priceRepository.findLatestTradeDate()
                    .orElseThrow(() -> new DateNotFoundException("No trading data available in database"));
        }

        // Validate date exists
        if (!priceRepository.existsByTradeDate(searchDate)) {
            throw new DateNotFoundException("No data found for date: " + searchDate);
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "pctChange"));
        List<NseDailyPrice> losers = priceRepository.findTopLosers(searchDate, pageable);

        List<StockDto> stockDtos = losers.stream()
                .map(this::mapToStockDto)
                .collect(Collectors.toList());

        return TopMoversResponse.builder()
                .stocks(stockDtos)
                .tradeDate(searchDate)
                .category("LOSERS")
                .count(stockDtos.size())
                .build();
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    /**
     * Map NseDailyPrice entity to StockDto
     */
    private StockDto mapToStockDto(NseDailyPrice price) {
        return StockDto.builder()
                .id(price.getId())
                .tradeDate(price.getTradeDate())
                .symbol(price.getSymbol())
                .series(price.getSeries())
                .openPrice(price.getOpenPrice())
                .highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice())
                .closePrice(price.getClosePrice())
                .prevClose(price.getPrevClose())
                .pctChange(price.getPctChange())
                .tradedQuantity(price.getTradedQuantity())
                .turnover(price.getTurnover())
                .deliveryQty(price.getDeliveryQty())
                .deliveryPct(price.getDeliveryPct())
                .build();
    }

    /**
     * Map NseDailyPrice entity to StockDetailResponse (includes company info)
     */
    private StockDetailResponse mapToStockDetailDto(NseDailyPrice price) {
        return StockDetailResponse.builder()
                .id(price.getId())
                .tradeDate(price.getTradeDate())
                .symbol(price.getSymbol())
                .series(price.getSeries())
                .openPrice(price.getOpenPrice())
                .highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice())
                .closePrice(price.getClosePrice())
                .prevClose(price.getPrevClose())
                .pctChange(price.getPctChange())
                .tradedQuantity(price.getTradedQuantity())
                .turnover(price.getTurnover())
                .deliveryQty(price.getDeliveryQty())
                .deliveryPct(price.getDeliveryPct())
                .build();
    }
}
