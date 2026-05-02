package com.kisshore19.nseinsights.service;

import com.kisshore19.nseinsights.dto.response.*;
import com.kisshore19.nseinsights.entity.IndexDailyClose;
import com.kisshore19.nseinsights.entity.IndexMaster;
import com.kisshore19.nseinsights.entity.IndexSectorMap;
import com.kisshore19.nseinsights.entity.NseDailyPrice;
import com.kisshore19.nseinsights.exception.DateNotFoundException;
import com.kisshore19.nseinsights.exception.InvalidDateException;
import com.kisshore19.nseinsights.repository.IndexDailyCloseRepository;
import com.kisshore19.nseinsights.repository.IndexMasterRepository;
import com.kisshore19.nseinsights.repository.IndexSectorMapRepository;
import com.kisshore19.nseinsights.repository.NseDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Module 2 - Data Explorer APIs.
 * Handles stock search, filtering, and analytics queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExplorerService {

    private final NseDailyPriceRepository    priceRepository;
    private final IndexMasterRepository      indexMasterRepository;
    private final IndexSectorMapRepository   indexSectorMapRepository;
    private final IndexDailyCloseRepository  indexDailyCloseRepository;

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
  /*  public StockSearchResponse searchStocks(
            String dateStr,
            String symbol,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long minVolume,
            BigDecimal minPctChange,
            BigDecimal maxPctChange,
            BigDecimal minDeliveryPct,
            int pageable) {

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
    }*/

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
                .totalDays(dates.size())
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
     * API 5: Get top gainers for a date, optionally filtered by index
     */
    public TopMoversResponse getTopGainers(String dateStr, int limit, String indexName) {
        log.info("Fetching top {} gainers for date: {}, index: {}", limit, dateStr, indexName);

        LocalDate searchDate = resolveDate(dateStr);

        List<NseDailyPrice> gainers;
        if (indexName != null && !indexName.isBlank()) {
            List<String> symbols = getActiveSymbolsForIndex(indexName);
            gainers = priceRepository.findTopGainersForSymbols(searchDate, symbols, limit);
        } else {
            gainers = priceRepository.findTopGainers(searchDate, limit);
        }

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
     * API 6: Get top losers for a date, optionally filtered by index
     */
    public TopMoversResponse getTopLosers(String dateStr, int limit, String indexName) {
        log.info("Fetching top {} losers for date: {}, index: {}", limit, dateStr, indexName);

        LocalDate searchDate = resolveDate(dateStr);

        List<NseDailyPrice> losers;
        if (indexName != null && !indexName.isBlank()) {
            List<String> symbols = getActiveSymbolsForIndex(indexName);
            losers = priceRepository.findTopLosersForSymbols(searchDate, symbols, limit);
        } else {
            losers = priceRepository.findTopLosers(searchDate, limit);
        }

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

    /**
     * Get all sectors with constituent stocks, index memberships, and sectoral index stats.
     * Uses 4 queries total regardless of sector/stock count.
     */
    public SectorGroupResponse getStocksGroupedBySector(String dateStr) {
        LocalDate searchDate = resolveDate(dateStr);

        // Q1: all active index_master rows
        List<IndexMaster> allMappings = indexMasterRepository.findAll().stream()
                .filter(IndexMaster::getIsActive)
                .collect(Collectors.toList());

        if (allMappings.isEmpty()) {
            throw new DateNotFoundException(
                    "index_master is empty. Run POST /api/v1/index-constituents/sync first.");
        }

        // symbol → [index names], symbol → sector, indexKey → sector
        Map<String, List<String>> symbolToIndices  = new LinkedHashMap<>();
        Map<String, String>       symbolToSector   = new LinkedHashMap<>();
        Map<String, String>       indexKeyToSector = new LinkedHashMap<>();

        for (IndexMaster m : allMappings) {
            symbolToIndices.computeIfAbsent(m.getSymbol(), k -> new ArrayList<>())
                           .add(m.getIndexName());
            symbolToSector.putIfAbsent(m.getSymbol(),
                    m.getSector() != null ? m.getSector() : "Unknown");
            if (m.getSector() != null) {
                indexKeyToSector.putIfAbsent(m.getIndexName(), m.getSector());
            }
        }

        // Q2: index_sector_map → indexKey → dailyCloseName (e.g. "NIFTYIT" → "Nifty IT")
        Map<String, String> indexKeyToDailyClose = indexSectorMapRepository.findAll().stream()
                .collect(Collectors.toMap(IndexSectorMap::getIndexKey,
                                          IndexSectorMap::getDailyCloseName));

        // sector → (indexKey, dailyCloseName) — first sectoral index found for each sector
        Map<String, String[]> sectorToSectoralIndex = new LinkedHashMap<>();
        indexKeyToSector.forEach((indexKey, sector) -> {
            if (indexKeyToDailyClose.containsKey(indexKey)) {
                sectorToSectoralIndex.putIfAbsent(sector,
                        new String[]{ indexKey, indexKeyToDailyClose.get(indexKey) });
            }
        });

        // Q3: prices for all unique symbols
        List<String> allSymbols = new ArrayList<>(symbolToSector.keySet());
        Map<String, NseDailyPrice> priceBySymbol = priceRepository
                .findByTradeDateAndSymbolIn(searchDate, allSymbols)
                .stream()
                .collect(Collectors.toMap(NseDailyPrice::getSymbol, p -> p));

        // Q4: sectoral index daily-close stats for the date
        List<String> sectorIndexDailyCloseNames = sectorToSectoralIndex.values().stream()
                .map(arr -> arr[1])
                .distinct()
                .collect(Collectors.toList());
        Map<String, IndexDailyClose> closeByName = indexDailyCloseRepository
                .findByTradeDateAndIndexNameIn(searchDate, sectorIndexDailyCloseNames)
                .stream()
                .collect(Collectors.toMap(IndexDailyClose::getIndexName, c -> c));

        // Group unique symbols by sector
        Map<String, List<String>> sectorToSymbols = new LinkedHashMap<>();
        symbolToSector.forEach((sym, sec) ->
                sectorToSymbols.computeIfAbsent(sec, k -> new ArrayList<>()).add(sym));

        List<SectorGroupResponse.SectorGroup> groups = sectorToSymbols.entrySet().stream()
                .map(e -> buildSectorGroup(
                        e.getKey(), e.getValue(),
                        symbolToIndices, priceBySymbol,
                        sectorToSectoralIndex, closeByName))
                .sorted(Comparator.comparing(SectorGroupResponse.SectorGroup::getSectorName))
                .collect(Collectors.toList());

        return SectorGroupResponse.builder()
                .tradeDate(searchDate)
                .totalSectors(groups.size())
                .sectors(groups)
                .build();
    }

    private SectorGroupResponse.SectorGroup buildSectorGroup(
            String sectorName,
            List<String> symbols,
            Map<String, List<String>> symbolToIndices,
            Map<String, NseDailyPrice> priceBySymbol,
            Map<String, String[]> sectorToSectoralIndex,
            Map<String, IndexDailyClose> closeByName) {

        List<SectorGroupResponse.SectorStockDto> stocks = symbols.stream()
                .filter(priceBySymbol::containsKey)
                .map(symbol -> {
                    NseDailyPrice p = priceBySymbol.get(symbol);
                    return SectorGroupResponse.SectorStockDto.builder()
                            .symbol(symbol)
                            .indices(symbolToIndices.getOrDefault(symbol, List.of()))
                            .openPrice(p.getOpenPrice())
                            .highPrice(p.getHighPrice())
                            .lowPrice(p.getLowPrice())
                            .closePrice(p.getClosePrice())
                            .prevClose(p.getPrevClose())
                            .pctChange(p.getPctChange())
                            .tradedQuantity(p.getTradedQuantity())
                            .turnover(p.getTurnover())
                            .deliveryQty(p.getDeliveryQty())
                            .deliveryPct(p.getDeliveryPct())
                            .build();
                })
                .sorted(Comparator.comparing(SectorGroupResponse.SectorStockDto::getPctChange,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        List<BigDecimal> changes = stocks.stream()
                .map(SectorGroupResponse.SectorStockDto::getPctChange)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        BigDecimal avg = changes.isEmpty() ? null :
                changes.stream()
                       .reduce(BigDecimal.ZERO, BigDecimal::add)
                       .divide(BigDecimal.valueOf(changes.size()), 2, RoundingMode.HALF_UP);

        // Attach sectoral index stats if available
        String[]        sectoralIdx  = sectorToSectoralIndex.get(sectorName);
        IndexDailyClose idxClose     = sectoralIdx != null ? closeByName.get(sectoralIdx[1]) : null;

        return SectorGroupResponse.SectorGroup.builder()
                .sectorName(sectorName)
                .stockCount(symbols.size())
                .matchedCount(stocks.size())
                .avgPctChange(avg)
                .maxPctChange(changes.stream().max(Comparator.naturalOrder()).orElse(null))
                .minPctChange(changes.stream().min(Comparator.naturalOrder()).orElse(null))
                .sectorIndexKey(sectoralIdx  != null ? sectoralIdx[0]          : null)
                .sectorIndexName(sectoralIdx != null ? sectoralIdx[1]          : null)
                .indexClose(idxClose         != null ? idxClose.getCloseValue() : null)
                .indexPctChange(idxClose     != null ? idxClose.getPctChange()  : null)
                .indexPe(idxClose            != null ? idxClose.getPe()         : null)
                .indexPb(idxClose            != null ? idxClose.getPb()         : null)
                .indexDivYield(idxClose      != null ? idxClose.getDivYield()   : null)
                .stocks(stocks)
                .build();
    }

    /**
     * Get all indices with their constituent stocks and price data for a given date.
     * Uses 2 queries total: one for all active index-symbol mappings, one for all prices.
     */
    public IndexGroupResponse getStocksGroupedByIndex(String dateStr) {
        LocalDate searchDate = resolveDate(dateStr);

        // Query 1: all active index-master rows
        List<IndexMaster> allMappings = indexMasterRepository.findAll().stream()
                .filter(IndexMaster::getIsActive)
                .collect(Collectors.toList());

        if (allMappings.isEmpty()) {
            throw new DateNotFoundException(
                    "index_master is empty. Run POST /api/v1/index-constituents/sync first.");
        }

        // Build indexName → [symbols] map, preserving insertion order
        Map<String, List<String>> indexToSymbols = new LinkedHashMap<>();
        for (IndexMaster m : allMappings) {
            indexToSymbols.computeIfAbsent(m.getIndexName(), k -> new ArrayList<>())
                          .add(m.getSymbol());
        }

        // Query 2: all prices for those symbols on the date in one shot
        List<String> allSymbols = allMappings.stream()
                .map(IndexMaster::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        Map<String, StockDto> priceBySymbol = priceRepository
                .findByTradeDateAndSymbolIn(searchDate, allSymbols)
                .stream()
                .collect(Collectors.toMap(NseDailyPrice::getSymbol, this::mapToStockDto));

        // Build response groups
        List<IndexGroupResponse.IndexGroup> groups = indexToSymbols.entrySet().stream()
                .map(entry -> buildIndexGroup(entry.getKey(), entry.getValue(), priceBySymbol))
                .collect(Collectors.toList());

        return IndexGroupResponse.builder()
                .tradeDate(searchDate)
                .totalIndices(groups.size())
                .indices(groups)
                .build();
    }

    private IndexGroupResponse.IndexGroup buildIndexGroup(
            String indexName, List<String> symbols, Map<String, StockDto> priceBySymbol) {

        List<StockDto> stocks = symbols.stream()
                .filter(priceBySymbol::containsKey)
                .map(priceBySymbol::get)
                .sorted(Comparator.comparing(StockDto::getPctChange,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        List<BigDecimal> changes = stocks.stream()
                .map(StockDto::getPctChange)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        BigDecimal avg = changes.isEmpty() ? null :
                changes.stream()
                       .reduce(BigDecimal.ZERO, BigDecimal::add)
                       .divide(BigDecimal.valueOf(changes.size()), 2, RoundingMode.HALF_UP);

        BigDecimal max = changes.stream().max(Comparator.naturalOrder()).orElse(null);
        BigDecimal min = changes.stream().min(Comparator.naturalOrder()).orElse(null);

        return IndexGroupResponse.IndexGroup.builder()
                .indexName(indexName)
                .stockCount(symbols.size())
                .matchedCount(stocks.size())
                .avgPctChange(avg)
                .maxPctChange(max)
                .minPctChange(min)
                .stocks(stocks)
                .build();
    }

    /**
     * Get all stocks in an index for a given date with their daily price data.
     */
    public TopMoversResponse getStocksForIndex(String indexName, String dateStr) {
        log.info("Fetching all stocks for index: {}, date: {}", indexName, dateStr);

        LocalDate searchDate = resolveDate(dateStr);
        List<String> symbols = getActiveSymbolsForIndex(indexName);

        List<StockDto> stockDtos = priceRepository
                .findByTradeDateAndSymbolIn(searchDate, symbols)
                .stream()
                .map(this::mapToStockDto)
                .collect(Collectors.toList());

        return TopMoversResponse.builder()
                .stocks(stockDtos)
                .tradeDate(searchDate)
                .category(indexName.toUpperCase())
                .count(stockDtos.size())
                .build();
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private LocalDate resolveDate(String dateStr) {
        LocalDate tradeDate = parseUserDate(dateStr);
        if (tradeDate == null) {
            tradeDate = priceRepository.findLatestTradeDate()
                    .orElseThrow(() -> new DateNotFoundException("No trading data available in database"));
        }
        if (!priceRepository.existsByTradeDate(tradeDate)) {
            throw new DateNotFoundException("No data found for date: " + tradeDate);
        }
        return tradeDate;
    }

    private List<String> getActiveSymbolsForIndex(String indexName) {
        List<String> symbols = indexMasterRepository
                .findByIndexNameAndIsActiveTrue(indexName.toUpperCase())
                .stream()
                .map(IndexMaster::getSymbol)
                .collect(Collectors.toList());
        if (symbols.isEmpty()) {
            throw new DateNotFoundException(
                    "No constituents found for index: " + indexName
                            + ". Run POST /api/v1/index-ingestion/sync first.");
        }
        return symbols;
    }

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
