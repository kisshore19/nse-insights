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
