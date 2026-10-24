# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Spring Boot 3.3.5 / Java 17 (Gradle toolchain). The repo has no `gradlew` script (only `gradle/wrapper/gradle-wrapper.properties`), so use a system `gradle`.

- Build: `gradle build`
- Run: `gradle bootRun` (JDWP debug agent listens on port 5005; app on 8080)
- Test: `gradle test`; single test: `gradle test --tests 'com.kisshore19.nseinsights.SomeTest'`. There is currently no `src/test` directory.

Runtime dependencies: MySQL (`nse_insights` DB on localhost:3306) and Redis (localhost:6379, used as Spring cache). `spring.jpa.hibernate.ddl-auto=validate`, so the schema must exist first. Create it with `src/main/resources/schema.sql` (or the root `DATABASE_SETUP.sql` / `setup_database.*` scripts). Entity changes need a matching manual schema change.

## Architecture

A single-module Spring Boot app that ingests NSE (India) market data, derives aggregated candles and stats, and serves them over REST to static HTML UIs in `src/main/resources/static/` (served at `http://localhost:8080/`: `index.html` hub with tabs for `stats-insights.html`, `screener.html`, `admin-dashboard.html`).

Layering is the usual `controller → service → repository (Spring Data JPA, with projections and Specifications) → entity`, with DTOs under `dto/request` and `dto/response`. Responses are wrapped in `ApiResponse`, and errors go through `exception/GlobalExceptionHandler`. Lombok and MapStruct are used (both are annotation processors).

Data pipeline, in order:
1. **Ingestion** (`/api/v1/ingestion`, `/api/v1/index-ingestion`): `NseBhavatopyDownloader`, `MtoFileDownloader` (delivery data), `NseIndexDownloader` and `NseIndexCloseDownloader` download files from NSE with `WebClient` (`WebClientConfig`). Downloads run on a thread pool (`ThreadPoolConfig`, `nse.download.thread-pool-size`, which NSE may block above about 10). Files are saved to disk (`nse.bhavdata.storage-path`, `nse.index.storage-path`), then parsed by `CsvParserService`, `IndexCsvParserService` and `IndexCloseCsvParser` (OpenCSV). Results are persisted to `nse_daily_price` and `index_daily_close`, with `download_log` tracking history. Rows are unique on (trade_date, symbol, series).
2. **Index constituents** (`/api/v1/index-constituents`): syncs `IndexMaster` and `IndexSectorMap`. These power the by-index and by-sector views and the F&O filter.
3. **Candles / stats** (`/api/v1/candles`, `/api/v1/index-candles`): `CandleService` and `CandleStatsService` build WEEK/MONTH/QUARTER/YEAR aggregates from `nse_daily_price` using native CTE queries. `IndexCandleStatsService` does the same for indices. Period keys are resolved by `util/PeriodKeyUtils`. Stats builds are additive and skip symbols already stored for a period. They never overwrite existing rows. `*/stats/screen` endpoints filter the stats via JPA `Specification` classes (`CandleStatsSpecification`, `IndexCandleStatsSpecification`), and `ScreenerService` backs the screener UI.
4. **Explorer** (`/api/v1/stocks`): `DataExplorerService` and `StockExplorerController` provide search, top gainers/losers, and grouping by index or sector.

`backtesting/` (`BhavcopyProcessor`, `SetupScanner`, `EarlyQuarterPredictor`) holds standalone analysis code outside the REST flow.

## Config gotchas

- `application.properties` holds Windows storage paths (`E:/StockResearch/...`) and committed DB credentials. On Linux these resolve to a literal relative `E:/` directory in the repo, which is untracked, so override `nse.bhavdata.storage-path` and `nse.index.storage-path` locally rather than committing the change.
- SQL and bind-parameter logging is enabled at INFO.
- API docs and examples: `src/main/resources/Api's.md`, `CURL_COMMANDS_REFERENCE.md`, the Postman collections at the root, and `PROJECT_INDEX.md`. The index says Java 21, but `build.gradle` uses 17, which is authoritative.
