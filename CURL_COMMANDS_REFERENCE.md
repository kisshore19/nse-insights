# 🔗 cURL Command Reference - NSE Insights API

Complete cURL commands for all API endpoints. Use these if you prefer command-line testing.

---

## 🔧 Base Configuration

```bash
# Set base URL as variable (change if needed)
BASE_URL="http://localhost:8080"

# Or use directly in commands:
curl http://localhost:8080/api/v1/...
```

---

## 📌 MODULE 1: DATA INGESTION

### 1️⃣ Get Available Dates
```bash
curl -X GET "${BASE_URL}/api/v1/ingestion/available-dates" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json"
```

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/ingestion/available-dates"
```

**Expected Response:**
```json
{
  "status": "SUCCESS",
  "data": {
    "dates": ["2025-01-15", "2025-01-14"],
    "totalDates": 2,
    "latestDate": "2025-01-15",
    "oldestDate": "2025-01-14"
  }
}
```

---

### 2️⃣ Download & Parse NSE Data
```bash
curl -X POST "${BASE_URL}/api/v1/ingestion/download" \
  -H "Content-Type: application/json" \
  -d '{
    "tradeDate": "2025-01-15",
    "overwrite": false
  }'
```

**Example with variables:**
```bash
TRADE_DATE="2025-01-15"
curl -X POST "http://localhost:8080/api/v1/ingestion/download" \
  -H "Content-Type: application/json" \
  -d "{\"tradeDate\": \"${TRADE_DATE}\", \"overwrite\": false}"
```

**With overwrite:**
```bash
curl -X POST "http://localhost:8080/api/v1/ingestion/download" \
  -H "Content-Type: application/json" \
  -d '{"tradeDate": "2025-01-15", "overwrite": true}'
```

**Expected Response (Success):**
```json
{
  "status": "SUCCESS",
  "data": {
    "tradeDate": "2025-01-15",
    "recordsLoaded": 1500,
    "bhavatopyFile": "cm15JAN2025bhav.csv",
    "mtoFile": "MTO_15012025.DAT",
    "timeTakenMs": 5234,
    "downloadLogId": 1
  }
}
```

---

### 3️⃣ Get Download History
```bash
# Get page 0, 20 records per page, all statuses
curl -X GET "${BASE_URL}/api/v1/ingestion/history?page=0&size=20&status=ALL"

# Get only successful downloads
curl -X GET "${BASE_URL}/api/v1/ingestion/history?page=0&size=20&status=SUCCESS"

# Get failed downloads
curl -X GET "${BASE_URL}/api/v1/ingestion/history?page=0&size=20&status=FAILED"

# Get with pagination
curl -X GET "${BASE_URL}/api/v1/ingestion/history?page=1&size=50&status=ALL"
```

**Full example:**
```bash
curl -X GET "http://localhost:8080/api/v1/ingestion/history?page=0&size=20&status=ALL" \
  -H "Accept: application/json"
```

---

### 4️⃣ Get Download Status by Date
```bash
# Check if data exists for a specific date
curl -X GET "${BASE_URL}/api/v1/ingestion/status/2025-01-15"

# With variables
TRADE_DATE="2025-01-15"
curl -X GET "${BASE_URL}/api/v1/ingestion/status/${TRADE_DATE}"
```

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/ingestion/status/2025-01-15"
```

---

### 5️⃣ Delete Data by Date
```bash
# Delete all records for a specific date
curl -X DELETE "${BASE_URL}/api/v1/ingestion/2025-01-15"

# With variables
TRADE_DATE="2025-01-15"
curl -X DELETE "${BASE_URL}/api/v1/ingestion/${TRADE_DATE}"
```

**Example:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/ingestion/2025-01-15"
```

---

### 6️⃣ Get Ingestion Summary
```bash
curl -X GET "${BASE_URL}/api/v1/ingestion/summary" \
  -H "Accept: application/json"
```

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/ingestion/summary"
```

---

## 📊 MODULE 2: DATA EXPLORER

### 1️⃣ Search Stocks (Basic)
```bash
# Get all stocks for a date
curl -X GET "${BASE_URL}/api/v1/stocks?date=15-01-2025&page=0&size=50"

# With JSON pretty print
curl -s "${BASE_URL}/api/v1/stocks?date=15-01-2025&page=0&size=50" | jq '.'
```

**Example:**
```bash
curl "http://localhost:8080/api/v1/stocks?date=15-01-2025&page=0&size=50"
```

### 1️⃣ Search Stocks (Advanced Filters)
```bash
# Filter by price range and volume
curl -X GET "${BASE_URL}/api/v1/stocks?date=15-01-2025&minPrice=100&maxPrice=5000&minVolume=100000&page=0&size=50"

# Filter by percentage change
curl -X GET "${BASE_URL}/api/v1/stocks?date=15-01-2025&minPctChange=-5&maxPctChange=5&page=0&size=50"

# Filter by delivery percentage
curl -X GET "${BASE_URL}/api/v1/stocks?date=15-01-2025&minDeliveryPct=50&page=0&size=50"

# All filters combined with sorting
curl -X GET "${BASE_URL}/api/v1/stocks?date=15-01-2025&minPrice=100&maxPrice=5000&minVolume=100000&minPctChange=-5&maxPctChange=5&minDeliveryPct=50&page=0&size=50&sortBy=pctChange&sortDir=DESC"
```

**Full example with pretty print:**
```bash
curl -s "http://localhost:8080/api/v1/stocks?date=15-01-2025&minPrice=100&maxPrice=5000&page=0&size=10&sortBy=pctChange&sortDir=DESC" | jq '.'
```

---

### 2️⃣ Get Available Dates
```bash
curl -X GET "${BASE_URL}/api/v1/stocks/dates"

# With pretty print
curl -s "${BASE_URL}/api/v1/stocks/dates" | jq '.'
```

**Example:**
```bash
curl "http://localhost:8080/api/v1/stocks/dates" | jq '.'
```

---

### 3️⃣ Get Stock Detail
```bash
# Get INFY data for latest date
curl -X GET "${BASE_URL}/api/v1/stocks/INFY"

# Get INFY data for specific date (dd-MM-yyyy format!)
curl -X GET "${BASE_URL}/api/v1/stocks/INFY?date=15-01-2025"

# With variables
SYMBOL="INFY"
DATE="15-01-2025"
curl -X GET "${BASE_URL}/api/v1/stocks/${SYMBOL}?date=${DATE}"
```

**Example for multiple stocks:**
```bash
# Get INFY
curl "http://localhost:8080/api/v1/stocks/INFY?date=15-01-2025" | jq '.'

# Get TCS
curl "http://localhost:8080/api/v1/stocks/TCS?date=15-01-2025" | jq '.'

# Get RELIANCE
curl "http://localhost:8080/api/v1/stocks/RELIANCE?date=15-01-2025" | jq '.'
```

---

### 4️⃣ Get All Sectors
```bash
curl -X GET "${BASE_URL}/api/v1/stocks/sectors"

# With pretty print
curl -s "${BASE_URL}/api/v1/stocks/sectors" | jq '.'
```

**Example:**
```bash
curl "http://localhost:8080/api/v1/stocks/sectors" | jq '.'
```

---

### 5️⃣ Get Top Gainers
```bash
# Get top 10 gainers for latest date
curl -X GET "${BASE_URL}/api/v1/stocks/top-gainers"

# Get top 10 gainers for specific date
curl -X GET "${BASE_URL}/api/v1/stocks/top-gainers?date=15-01-2025&limit=10"

# Get top 5 gainers
curl -X GET "${BASE_URL}/api/v1/stocks/top-gainers?limit=5"

# With variables
DATE="15-01-2025"
LIMIT="10"
curl -X GET "${BASE_URL}/api/v1/stocks/top-gainers?date=${DATE}&limit=${LIMIT}"
```

**Example:**
```bash
curl "http://localhost:8080/api/v1/stocks/top-gainers?date=15-01-2025&limit=10" | jq '.'
```

---

### 6️⃣ Get Top Losers
```bash
# Get top 10 losers for latest date
curl -X GET "${BASE_URL}/api/v1/stocks/top-losers"

# Get top 10 losers for specific date
curl -X GET "${BASE_URL}/api/v1/stocks/top-losers?date=15-01-2025&limit=10"

# Get top 5 losers
curl -X GET "${BASE_URL}/api/v1/stocks/top-losers?limit=5"
```

**Example:**
```bash
curl "http://localhost:8080/api/v1/stocks/top-losers?date=15-01-2025&limit=10" | jq '.'
```

---

## 🎯 Quick Test Script

Save this as `test_api.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
DATE_FORMAT="15-01-2025"  # dd-MM-yyyy format
DATE_SQL="2025-01-15"     # yyyy-MM-dd format

echo "=== NSE Insights API Test Script ==="
echo ""

# Test 1: Check available dates
echo "1. Get Available Dates (Module 1)"
curl -s "${BASE_URL}/api/v1/ingestion/available-dates" | jq '.'
echo ""

# Test 2: Check available stock dates
echo "2. Get Available Stock Dates (Module 2)"
curl -s "${BASE_URL}/api/v1/stocks/dates" | jq '.'
echo ""

# Test 3: Get all sectors
echo "3. Get All Sectors"
curl -s "${BASE_URL}/api/v1/stocks/sectors" | jq '.'
echo ""

# Test 4: Search stocks
echo "4. Search Stocks"
curl -s "${BASE_URL}/api/v1/stocks?date=${DATE_FORMAT}&page=0&size=10" | jq '.'
echo ""

# Test 5: Get top gainers
echo "5. Get Top Gainers"
curl -s "${BASE_URL}/api/v1/stocks/top-gainers?date=${DATE_FORMAT}&limit=5" | jq '.'
echo ""

# Test 6: Get top losers
echo "6. Get Top Losers"
curl -s "${BASE_URL}/api/v1/stocks/top-losers?date=${DATE_FORMAT}&limit=5" | jq '.'
echo ""

echo "=== Test Complete ==="
```

**Run it:**
```bash
chmod +x test_api.sh
./test_api.sh
```

---

## 💾 Save Response to File

```bash
# Save response to file
curl -X GET "http://localhost:8080/api/v1/stocks/dates" \
  -o response.json

# View file
cat response.json | jq '.'

# Append to file
curl -X GET "http://localhost:8080/api/v1/stocks/sectors" \
  >> response.json
```

---

## ⏱️ Performance Testing

```bash
# Check response time
curl -w "\n\nResponse time: %{time_total}s\n" \
  "http://localhost:8080/api/v1/stocks?date=15-01-2025&page=0&size=100"

# Get detailed timing information
curl -w "\nTime Connect: %{time_connect}s\nTime TTFB: %{time_starttransfer}s\nTime Total: %{time_total}s\n" \
  "http://localhost:8080/api/v1/stocks/top-gainers?limit=10"
```

---

## 🔍 Debugging with Verbose Output

```bash
# Show request and response headers
curl -v "http://localhost:8080/api/v1/stocks/dates"

# Show only headers, no body
curl -I "http://localhost:8080/api/v1/stocks/dates"

# Trace all interactions
curl --trace - "http://localhost:8080/api/v1/stocks/dates"
```

---

## 📝 Batch Processing

Process multiple symbols:

```bash
#!/bin/bash

SYMBOLS=("INFY" "TCS" "WIPRO" "RELIANCE" "HDFC" "ICICIBANK" "AXISBANK")
DATE="15-01-2025"
BASE_URL="http://localhost:8080"

for symbol in "${SYMBOLS[@]}"; do
  echo "Getting data for $symbol..."
  curl -s "${BASE_URL}/api/v1/stocks/${symbol}?date=${DATE}" | jq '.data | {symbol: .symbol, price: .closePrice, change: .pctChange}'
done
```

---

## 🚀 Advanced Examples

### Example 1: Download Data & Verify
```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
TRADE_DATE="2025-01-15"

# Download data
echo "Downloading data for ${TRADE_DATE}..."
curl -X POST "${BASE_URL}/api/v1/ingestion/download" \
  -H "Content-Type: application/json" \
  -d "{\"tradeDate\": \"${TRADE_DATE}\", \"overwrite\": false}" | jq '.'

# Wait a moment
sleep 2

# Verify download
echo "Verifying download..."
curl -s "${BASE_URL}/api/v1/ingestion/status/${TRADE_DATE}" | jq '.'

# Get summary
echo "Getting summary..."
curl -s "${BASE_URL}/api/v1/ingestion/summary" | jq '.'
```

### Example 2: Find Highest Volume Stocks
```bash
curl -s "http://localhost:8080/api/v1/stocks?date=15-01-2025&page=0&size=100&sortBy=tradedQuantity&sortDir=DESC" | \
  jq '.data.content | .[0:10] | .[] | {symbol: .symbol, volume: .tradedQuantity, price: .closePrice}'
```

### Example 3: Find Best Performers
```bash
curl -s "http://localhost:8080/api/v1/stocks/top-gainers?date=15-01-2025&limit=20" | \
  jq '.data.stocks | .[] | {symbol: .symbol, price: .closePrice, gain: .pctChange}'
```

---

## ✅ Testing Checklist

- [ ] API is running on `localhost:8080`
- [ ] Database is populated with sample data
- [ ] Can GET available dates
- [ ] Can POST download request
- [ ] Can search stocks with filters
- [ ] Can get top gainers/losers
- [ ] Can get individual stock details
- [ ] Can download data without errors

---

**Happy Testing!** 🎉
