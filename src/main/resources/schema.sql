-- ============================================================
-- NSE Insights — MySQL Schema
-- Run this script once to create the database and tables
-- ============================================================

CREATE DATABASE IF NOT EXISTS nse_insights
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nse_insights;

-- ── nse_daily_price ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nse_daily_price (
    id               BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trade_date       DATE             NOT NULL,
    symbol           VARCHAR(20)      NOT NULL,
    series           VARCHAR(5)       NOT NULL DEFAULT 'EQ',
    open_price       DECIMAL(12,2)    NOT NULL,
    high_price       DECIMAL(12,2)    NOT NULL,
    low_price        DECIMAL(12,2)    NOT NULL,
    close_price      DECIMAL(12,2)    NOT NULL,
    prev_close       DECIMAL(12,2)    NULL,
    pct_change       DECIMAL(7,2)     NULL,
    traded_quantity  BIGINT           NOT NULL,
    turnover         DECIMAL(18,2)    NULL,
    delivery_qty     BIGINT           NULL,
    delivery_pct     DECIMAL(7,2)     NULL,
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_price_date_symbol UNIQUE (trade_date, symbol, series)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_price_date        ON nse_daily_price (trade_date DESC);
CREATE INDEX idx_price_symbol      ON nse_daily_price (symbol);
CREATE INDEX idx_price_pct_change  ON nse_daily_price (trade_date, pct_change DESC);
CREATE INDEX idx_price_volume      ON nse_daily_price (trade_date, traded_quantity DESC);

-- ── download_log ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS download_log (
    id               INT              NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trade_date       DATE             NOT NULL,
    status           VARCHAR(20)      NOT NULL,
    bhavacopy_url    VARCHAR(500)     NULL,
    mto_url          VARCHAR(500)     NULL,
    record_count     INT              NULL,
    file_name        VARCHAR(200)     NULL,
    error_message    TEXT             NULL,
    downloaded_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     DATETIME         NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_log_date   ON download_log (trade_date DESC);
CREATE INDEX idx_log_status ON download_log (status);

-- ── index_master ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS index_master (
    id               INT              NOT NULL AUTO_INCREMENT PRIMARY KEY,
    index_name       VARCHAR(50)      NOT NULL,
    symbol           VARCHAR(20)      NOT NULL,
    company_name     VARCHAR(200)     NULL,
    sector           VARCHAR(100)     NULL,
    industry         VARCHAR(100)     NULL,
    isin             VARCHAR(20)      NULL,
    is_active        TINYINT(1)       NOT NULL DEFAULT 1,
    added_date       DATE             NULL,
    updated_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_index_symbol UNIQUE (index_name, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idx_master_symbol ON index_master (symbol);
CREATE INDEX idx_idx_master_index  ON index_master (index_name);
CREATE INDEX idx_idx_master_sector ON index_master (sector);

-- ── comparison_insight ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS comparison_insight (
    id               BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date1            DATE             NOT NULL,
    date2            DATE             NOT NULL,
    insight_type     VARCHAR(50)      NOT NULL,
    symbol           VARCHAR(20)      NULL,
    insight_text     TEXT             NOT NULL,
    metric_value     DECIMAL(12,2)    NULL,
    source           VARCHAR(20)      NOT NULL,
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_insight_dates  ON comparison_insight (date1, date2);
CREATE INDEX idx_insight_type   ON comparison_insight (insight_type);
CREATE INDEX idx_insight_source ON comparison_insight (source);

-- ── ai_chat_session ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id               VARCHAR(36)      NOT NULL PRIMARY KEY,
    session_name     VARCHAR(200)     NULL,
    context_date     DATE             NULL,
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at   DATETIME         NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ai_chat_message ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id               BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id       VARCHAR(36)      NOT NULL,
    role             VARCHAR(20)      NOT NULL,
    message          TEXT             NOT NULL,
    tokens_used      INT              NULL,
    created_at       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session FOREIGN KEY (session_id)
        REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_role CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_msg_session ON ai_chat_message (session_id, created_at);


-- Run this to add the stock_candle table
CREATE TABLE IF NOT EXISTS stock_candle (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol              VARCHAR(20)     NOT NULL,
    timeframe           VARCHAR(10)     NOT NULL,
    candle_date         DATE            NOT NULL,
    candle_end_date     DATE            NOT NULL,

    -- OHLCV
    open_price          DECIMAL(12,2)   NOT NULL,
    high_price          DECIMAL(12,2)   NOT NULL,
    low_price           DECIMAL(12,2)   NOT NULL,
    close_price         DECIMAL(12,2)   NOT NULL,
    total_volume        BIGINT          NOT NULL,
    avg_volume          BIGINT          NULL,
    total_turnover      DECIMAL(18,2)   NULL,

    -- vs Previous Candle
    prev_close          DECIMAL(12,2)   NULL,
    pct_change          DECIMAL(7,2)    NULL,

    -- Delivery
    avg_delivery_pct    DECIMAL(7,2)    NULL,

    -- Candle Pattern
    pattern             VARCHAR(10)     NULL,   -- BULLISH / BEARISH / DOJI

    -- Body & Wick
    body_size           DECIMAL(12,2)   NULL,
    body_pct            DECIMAL(7,2)    NULL,
    upper_wick          DECIMAL(12,2)   NULL,
    lower_wick          DECIMAL(12,2)   NULL,
    upper_wick_pct      DECIMAL(7,2)    NULL,
    lower_wick_pct      DECIMAL(7,2)    NULL,

    trading_days        INT             NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NULL,

    CONSTRAINT uq_candle_symbol_timeframe_date
        UNIQUE (symbol, timeframe, candle_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_candle_symbol_tf   ON stock_candle (symbol, timeframe);
CREATE INDEX idx_candle_date        ON stock_candle (candle_date DESC);
CREATE INDEX idx_candle_pattern     ON stock_candle (timeframe, pattern);
CREATE INDEX idx_candle_pct         ON stock_candle (timeframe, pct_change DESC);

-- ── candle_stats table (fresh install) ───────────────────────────────────────
-- Stores pre-computed statistics per symbol per timeframe period.
-- Supports YEAR / QUARTER / MONTH / WEEK timeframes.
-- Run this once manually before starting the application.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE candle_stats (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    symbol          VARCHAR(20)     NOT NULL,
    timeframe       VARCHAR(10)     NOT NULL,

    -- Human-readable period identifier
    -- YEAR    → "2024"
    -- QUARTER → "2024-Q1"
    -- MONTH   → "2024-01"
    -- WEEK    → "2024-W03"
    period_key      VARCHAR(10)     NOT NULL,

    candle_date     DATE            NOT NULL,   -- first trading day of period
    candle_end_date DATE            NOT NULL,   -- last  trading day of period

    -- OHLC
    open_price      DECIMAL(12,2)   NULL,
    high_price      DECIMAL(12,2)   NULL,
    low_price       DECIMAL(12,2)   NULL,
    last_price      DECIMAL(12,2)   NULL,
    trend           VARCHAR(5)      NULL,       -- UP | DOWN | SIDE

    -- Volume extremes
    high_vol_qty    BIGINT          NULL,
    high_vol_date   DATE            NULL,
    low_vol_qty     BIGINT          NULL,
    low_vol_date    DATE            NULL,

    -- Delivery extremes
    high_deliv_qty  BIGINT          NULL,
    high_deliv_date DATE            NULL,
    low_deliv_qty   BIGINT          NULL,
    low_deliv_date  DATE            NULL,

    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_stats_symbol_timeframe_period (symbol, timeframe, period_key),
    INDEX idx_stats_symbol_timeframe            (symbol, timeframe),
    INDEX idx_stats_timeframe                   (timeframe)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;