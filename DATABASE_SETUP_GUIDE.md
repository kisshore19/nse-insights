## MySQL Database Setup - NSE Insights

### Overview
The NSE Insights application requires a MySQL 8.0+ database. This guide covers how to set up the database schema and populate sample data.

---

## Method 1: Using MySQL Command Line

### Step 1: Connect to MySQL Server
```bash
mysql -u root -p
# Enter your MySQL password when prompted
```

### Step 2: Execute the SQL script
Once connected to MySQL, run:
```sql
SOURCE E:/StockResearch/nse-insights/DATABASE_SETUP.sql;
```

Or on Linux/Mac:
```sql
SOURCE /path/to/StockResearch/nse-insights/DATABASE_SETUP.sql;
```

### Step 3: Verify the setup
```sql
USE nse_insights;
SHOW TABLES;
SELECT COUNT(*) as total_records FROM nse_daily_price;
```

---

## Method 2: Using MySQL Workbench

1. Open MySQL Workbench
2. Connect to your MySQL Server
3. Go to **File** → **Open SQL Script**
4. Select `DATABASE_SETUP.sql` from the project root
5. Click the **Execute** button (lightning bolt icon)
6. Review the output in the Results tab

---

## Method 3: Command Line (One Command)

```bash
mysql -u root -p"your_password" < E:\StockResearch\nse-insights\DATABASE_SETUP.sql
```

Replace `your_password` with your actual MySQL password.

---

## Method 4: Using Spring Boot Application

The application can automatically create the schema when it starts:

1. Ensure `spring.jpa.hibernate.ddl-auto=create` in `application.properties`
2. Run the application:
   ```bash
   gradle bootRun
   ```
3. Spring Data JPA will create all tables automatically

---

## Verifying the Setup

### Check if database exists
```sql
SHOW DATABASES;
```

### Check if all tables exist
```sql
USE nse_insights;
SHOW TABLES;
```

Expected output:
```
+------------------+
| Tables_in_nse_insights |
+------------------+
| ai_chat_message  |
| ai_chat_session  |
| comparison_insight |
| download_log     |
| index_master     |
| nse_daily_price  |
+------------------+
```

### Check table structure
```sql
DESCRIBE nse_daily_price;
DESCRIBE index_master;
DESCRIBE download_log;
```

### Check sample data
```sql
SELECT COUNT(*) FROM nse_daily_price;
SELECT COUNT(*) FROM index_master;
SELECT DISTINCT trade_date FROM nse_daily_price;
SELECT DISTINCT sector FROM index_master;
```

---

## Database Configuration in Spring Boot

The application uses these properties (in `application.properties`):

```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/nse_insights?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.format_sql=true
```

### Important Notes:
- **`ddl-auto=validate`**: Only validates existing schema (doesn't create)
- **`ddl-auto=create`**: Creates new schema on startup (WARNING: drops existing data)
- **`ddl-auto=update`**: Updates schema without losing data (safe option)

---

## Tables Created

### 1. **nse_daily_price**
Stock price data with OHLCV information
- Fields: trade_date, symbol, series, open_price, high_price, low_price, close_price, prev_close, pct_change, traded_quantity, turnover, delivery_qty, delivery_pct
- Unique constraint: (trade_date, symbol, series)
- Indexes: date, symbol, pct_change, volume

### 2. **download_log**
Tracks data ingestion operations
- Fields: trade_date, status, bhavacopy_url, mto_url, record_count, file_name, error_message, downloaded_at, completed_at
- Statuses: SUCCESS, FAILED, PARTIAL, DELETED

### 3. **index_master**
Index composition and company information
- Fields: index_name, symbol, company_name, sector, industry, isin, is_active
- Indexes: symbol, index_name, sector

### 4. **comparison_insight**
AI-generated insights from date comparisons (for Module 3)
- Fields: date1, date2, insight_type, symbol, insight_text, metric_value, source

### 5. **ai_chat_session**
Chat session management (for Module 3)
- Fields: id, session_name, context_date, created_at, last_active_at

### 6. **ai_chat_message**
Chat messages within sessions (for Module 3)
- Fields: session_id, role (USER/ASSISTANT), message, tokens_used

---

## Sample Data Included

The `DATABASE_SETUP.sql` script includes:
- 7 companies in index_master (IT, Banks, Oil & Gas sectors)
- 7 stock records for 2025-01-15

To skip sample data insertion, comment out the INSERT statements in the script.

---

## Troubleshooting

### Connection Refused
```
Error: Can't connect to MySQL server on 'localhost'
```
**Solution**: Ensure MySQL service is running
```bash
# On Windows
net start MySQL80

# On Mac
mysql.server start

# On Linux
sudo service mysql start
```

### Access Denied
```
Error: Access denied for user 'root'@'localhost' (using password: YES)
```
**Solution**: Verify MySQL credentials in `application.properties`

### Table Already Exists
```
Error: Table 'nse_daily_price' already exists
```
**Solution**: Either:
1. Use `CREATE TABLE IF NOT EXISTS` (already in script)
2. Drop the database: `DROP DATABASE nse_insights;`
3. Then re-run the script

### Character Set Issues
If you see encoding errors:
```sql
ALTER TABLE nse_daily_price CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## Quick Reference Commands

```sql
-- Check database size
SELECT table_name, ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size_mb
FROM information_schema.TABLES
WHERE table_schema = 'nse_insights';

-- Check row counts
SELECT TABLE_NAME, TABLE_ROWS
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'nse_insights';

-- View all indexes
SELECT * FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'nse_insights';

-- Reset auto_increment
ALTER TABLE nse_daily_price AUTO_INCREMENT = 1;
ALTER TABLE download_log AUTO_INCREMENT = 1;
```

---

## Next Steps

1. ✅ Run `DATABASE_SETUP.sql`
2. ✅ Verify all tables and sample data
3. ✅ Update `application.properties` with correct credentials
4. ✅ Start the Spring Boot application
5. ✅ Test APIs via REST endpoints or Postman

---

**Database Setup Complete!** 🎉

Your NSE Insights application is now ready with a fully configured MySQL database.
