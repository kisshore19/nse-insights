-- ============================================================
-- NSE Insights Database Setup & Verification Queries
-- Execute this file against MySQL to set up the database
-- ============================================================

-- Step 1: Create Database
CREATE DATABASE IF NOT EXISTS nse_insights
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE nse_insights;

-- Step 2: Create Tables
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

-- ============================================================
-- Step 3: Verification Queries
-- ============================================================

-- Show all created tables
SHOW TABLES;

-- Show structure of nse_daily_price
DESCRIBE nse_daily_price;

-- Show structure of download_log
DESCRIBE download_log;

-- Show structure of index_master
DESCRIBE index_master;

-- Show indexes on nse_daily_price
SHOW INDEX FROM nse_daily_price;

-- Show indexes on index_master
SHOW INDEX FROM index_master;

-- ============================================================
-- Step 4: Sample Data (Optional - for testing)
-- ============================================================

-- Insert sample index master data
INSERT IGNORE INTO index_master (index_name, symbol, company_name, sector, industry, isin, is_active, added_date, updated_at)
VALUES
('NIFTY50', 'INFY', 'Infosys Limited', 'Information Technology', 'IT Services', 'INE009A01021', 1, '2025-01-01', NOW()),
('NIFTY50', 'TCS', 'Tata Consultancy Services', 'Information Technology', 'IT Services', 'INE467B01029', 1, '2025-01-01', NOW()),
('NIFTY50', 'WIPRO', 'Wipro Limited', 'Information Technology', 'IT Services', 'INE066K01023', 1, '2025-01-01', NOW()),
('NIFTY50', 'RELIANCE', 'Reliance Industries Limited', 'Oil & Gas', 'Petroleum & Natural Gas', 'INE002A01018', 1, '2025-01-01', NOW()),
('NIFTY50', 'HDFC', 'Housing Development Finance Corp', 'Banks', 'Commercial Banks', 'INE001A01023', 1, '2025-01-01', NOW()),
('NIFTY50', 'ICICIBANK', 'ICICI Bank Limited', 'Banks', 'Commercial Banks', 'INE090A01021', 1, '2025-01-01', NOW()),
('NIFTY50', 'AXISBANK', 'Axis Bank Limited', 'Banks', 'Commercial Banks', 'INE238A01020', 1, '2025-01-01', NOW());

-- Sample stock price data for testing (2025-01-15)
INSERT IGNORE INTO nse_daily_price
(trade_date, symbol, series, open_price, high_price, low_price, close_price, prev_close, pct_change, traded_quantity, turnover, delivery_qty, delivery_pct, created_at)
VALUES
('2025-01-15', 'INFY', 'EQ', 1845.50, 1865.25, 1840.00, 1860.00, 1850.00, 0.54, 5234567, 9850000000.00, 2617283, 50.00, NOW()),
('2025-01-15', 'TCS', 'EQ', 3420.00, 3485.50, 3410.00, 3475.00, 3450.00, 0.72, 3456789, 12050000000.00, 1728394, 50.00, NOW()),
('2025-01-15', 'WIPRO', 'EQ', 445.50, 452.75, 442.00, 450.00, 448.00, 0.45, 8234567, 3750000000.00, 4117283, 50.00, NOW()),
('2025-01-15', 'HDFC', 'EQ', 2680.00, 2745.50, 2675.00, 2740.00, 2700.00, 1.48, 4567890, 12500000000.00, 2283945, 50.00, NOW()),
('2025-01-15', 'ICICIBANK', 'EQ', 980.00, 1005.00, 975.00, 1000.00, 980.00, 2.04, 7654321, 7600000000.00, 3827160, 50.00, NOW()),
('2025-01-15', 'AXISBANK', 'EQ', 1085.00, 1115.50, 1080.00, 1110.00, 1085.00, 2.30, 5234567, 5800000000.00, 2617283, 50.00, NOW()),
('2025-01-15', 'RELIANCE', 'EQ', 2780.00, 2825.00, 2770.00, 2815.00, 2800.00, 0.54, 6789012, 19150000000.00, 3394506, 50.00, NOW());

-- ============================================================
-- Step 5: Validation Queries
-- ============================================================

-- Count records
SELECT 'nse_daily_price' as table_name, COUNT(*) as record_count FROM nse_daily_price
UNION ALL
SELECT 'index_master' as table_name, COUNT(*) as record_count FROM index_master
UNION ALL
SELECT 'download_log' as table_name, COUNT(*) as record_count FROM download_log;

-- Show available dates
SELECT DISTINCT trade_date FROM nse_daily_price ORDER BY trade_date DESC;

-- Show sectors
SELECT DISTINCT sector FROM index_master WHERE is_active = 1 ORDER BY sector;

-- Show top gainers (2025-01-15)
SELECT symbol, close_price, pct_change FROM nse_daily_price
WHERE trade_date = '2025-01-15'
ORDER BY pct_change DESC LIMIT 5;

-- Show top losers (2025-01-15)
SELECT symbol, close_price, pct_change FROM nse_daily_price
WHERE trade_date = '2025-01-15'
ORDER BY pct_change ASC LIMIT 5;
