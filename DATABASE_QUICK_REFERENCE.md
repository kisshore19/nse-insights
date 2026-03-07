# NSE Insights Database Setup - Quick Reference

## 🚀 Quick Start (Choose One Method)

### Method 1: Windows Batch Script (Easiest)
```bash
cd E:\StockResearch\nse-insights
setup_database.bat
```

### Method 2: PowerShell Script
```powershell
cd E:\StockResearch\nse-insights
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
.\setup_database.ps1
```

### Method 3: Direct MySQL Command Line
```bash
mysql -u root -p"root" < E:\StockResearch\nse-insights\DATABASE_SETUP.sql
```

### Method 4: MySQL Interactive
```bash
mysql -u root -p
# Then type: SOURCE E:/StockResearch/nse-insights/DATABASE_SETUP.sql;
```

---

## ✅ Verification Commands

After running the setup, verify with these commands:

```sql
-- Show all tables
USE nse_insights;
SHOW TABLES;

-- Count records
SELECT 'nse_daily_price' as table_name, COUNT(*) as count FROM nse_daily_price
UNION ALL
SELECT 'index_master' as table_name, COUNT(*) as count FROM index_master
UNION ALL
SELECT 'download_log' as table_name, COUNT(*) as count FROM download_log;

-- View sample data
SELECT * FROM nse_daily_price LIMIT 5;
SELECT DISTINCT sector FROM index_master;
SELECT COUNT(*) FROM nse_daily_price WHERE trade_date = '2025-01-15';
```

---

## 📋 Files Created

| File | Purpose |
|------|---------|
| `DATABASE_SETUP.sql` | Main SQL script with all schema and sample data |
| `DATABASE_SETUP_GUIDE.md` | Detailed setup guide with troubleshooting |
| `setup_database.bat` | Windows batch script for automated setup |
| `setup_database.ps1` | PowerShell script for automated setup |
| `DATABASE_QUICK_REFERENCE.md` | This file |

---

## 🔧 Configuration

Update `application.properties` if needed:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/nse_insights?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=validate
```

---

## 🎯 Expected Results After Setup

### Tables Created (6 total)
- ✅ `nse_daily_price` - Stock price data (7 sample records for 2025-01-15)
- ✅ `download_log` - Ingestion logs
- ✅ `index_master` - Company master data (7 sample companies)
- ✅ `comparison_insight` - Module 3 insights
- ✅ `ai_chat_session` - Module 3 chat sessions
- ✅ `ai_chat_message` - Module 3 chat messages

### Indexes Created
- ✅ idx_price_date, idx_price_symbol, idx_price_pct_change, idx_price_volume
- ✅ idx_log_date, idx_log_status
- ✅ idx_idx_master_symbol, idx_idx_master_index, idx_idx_master_sector

### Sample Data
- ✅ 7 companies (INFY, TCS, WIPRO, RELIANCE, HDFC, ICICIBANK, AXISBANK)
- ✅ 7 stock records for trade_date = 2025-01-15

---

## 🐛 Troubleshooting

### Issue: "MySQL not found"
**Solution:** Update MYSQL_PATH in setup script or ensure MySQL bin directory is in PATH

### Issue: "Access Denied"
**Solution:** Verify username/password and that MySQL service is running

### Issue: "Table already exists"
**Solution:** This is fine - scripts use `IF NOT EXISTS`. Data will be skipped.

### Issue: Character encoding errors
**Solution:** Run this command:
```sql
ALTER TABLE nse_daily_price CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 📊 Sample Data Overview

### Index Master (7 companies)
```
NIFTY50, INFY, Infosys Limited, Information Technology, IT Services
NIFTY50, TCS, Tata Consultancy Services, Information Technology, IT Services
NIFTY50, WIPRO, Wipro Limited, Information Technology, IT Services
NIFTY50, RELIANCE, Reliance Industries Limited, Oil & Gas, Petroleum & Natural Gas
NIFTY50, HDFC, Housing Development Finance Corp, Banks, Commercial Banks
NIFTY50, ICICIBANK, ICICI Bank Limited, Banks, Commercial Banks
NIFTY50, AXISBANK, Axis Bank Limited, Banks, Commercial Banks
```

### Stock Price Data (2025-01-15)
- INFY: Close 1860.00, Change +0.54%
- TCS: Close 3475.00, Change +0.72%
- WIPRO: Close 450.00, Change +0.45%
- RELIANCE: Close 2815.00, Change +0.54%
- HDFC: Close 2740.00, Change +1.48%
- ICICIBANK: Close 1000.00, Change +2.04%
- AXISBANK: Close 1110.00, Change +2.30%

---

## 🚀 Next Steps After Database Setup

1. ✅ **Verify Database** - Run verification commands above
2. ✅ **Start Application**:
   ```bash
   cd E:\StockResearch\nse-insights
   gradle bootRun
   ```
3. ✅ **Test APIs** - Use Postman or cURL:
   ```bash
   curl http://localhost:8080/api/v1/stocks/dates
   curl http://localhost:8080/api/v1/stocks?date=15-01-2025&page=0&size=10
   curl http://localhost:8080/api/v1/stocks/INFY?date=15-01-2025
   curl http://localhost:8080/api/v1/stocks/top-gainers?limit=5
   ```

---

## 📞 Need Help?

Refer to `DATABASE_SETUP_GUIDE.md` for:
- Detailed setup instructions
- Multiple setup methods
- Troubleshooting guide
- Database configuration options
- Verification queries

---

**Last Updated:** March 7, 2026  
**Database Version:** MySQL 8.0+  
**Application:** NSE Insights  
**Status:** ✅ Ready for Deployment
