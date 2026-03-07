# 📬 Postman Collection - NSE Insights API

## Quick Start

### Import Collection into Postman

**Method 1: Direct Import**
1. Open Postman
2. Click **File** → **Import**
3. Select **NSE_Insights_Postman_Collection.json**
4. Click **Import**

**Method 2: Drag & Drop**
1. Open Postman
2. Drag `NSE_Insights_Postman_Collection.json` into Postman window
3. Click **Import**

**Method 3: URL Import**
1. Open Postman
2. Click **File** → **Import**
3. Select **Link** tab
4. Paste the collection URL
5. Click **Import**

---

## 🔧 Configure Environment Variables

### Base URL Variable
The collection uses `{{baseUrl}}` variable which defaults to: `http://localhost:8080`

**To change it:**
1. Click **Variables** at the top
2. Edit `baseUrl` value
3. Examples:
   - Local: `http://localhost:8080`
   - Staging: `http://staging.example.com:8080`
   - Production: `https://api.example.com`

---

## 📋 API Endpoints Summary

### Module 1: Data Ingestion (6 APIs)
| # | Method | Endpoint | Purpose |
|----|--------|----------|---------|
| 1 | GET | `/api/v1/ingestion/available-dates` | Get all available trade dates |
| 2 | POST | `/api/v1/ingestion/download` | Download & parse NSE data |
| 3 | GET | `/api/v1/ingestion/history` | Get download history (paginated) |
| 4 | GET | `/api/v1/ingestion/status/{date}` | Check if data exists for date |
| 5 | DELETE | `/api/v1/ingestion/{date}` | Delete data for a date |
| 6 | GET | `/api/v1/ingestion/summary` | Get ingestion statistics |

### Module 2: Data Explorer (6 APIs)
| # | Method | Endpoint | Purpose |
|----|--------|----------|---------|
| 1 | GET | `/api/v1/stocks` | Search stocks with filters |
| 2 | GET | `/api/v1/stocks/dates` | Get all available dates |
| 3 | GET | `/api/v1/stocks/{symbol}` | Get stock detail for a date |
| 4 | GET | `/api/v1/stocks/sectors` | Get all sectors |
| 5 | GET | `/api/v1/stocks/top-gainers` | Get top gainers |
| 6 | GET | `/api/v1/stocks/top-losers` | Get top losers |

---

## 🎯 Usage Examples

### 1️⃣ Download NSE Data for a Date
```
POST /api/v1/ingestion/download

Body (JSON):
{
  "tradeDate": "2025-01-15",
  "overwrite": false
}
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": {
    "tradeDate": "2025-01-15",
    "recordsLoaded": 1500,
    "bhavatopyFile": "cm15JAN2025bhav.csv",
    "mtoFile": "MTO_15012025.DAT",
    "timeTakenMs": 5234
  }
}
```

---

### 2️⃣ Search Stocks with Filters
```
GET /api/v1/stocks?date=15-01-2025&minPrice=100&maxPrice=5000&minVolume=100000&page=0&size=50&sortBy=pctChange&sortDir=DESC
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": {
    "content": [
      {
        "symbol": "AXISBANK",
        "closePrice": 1110.00,
        "pctChange": 2.30,
        "tradedQuantity": 5234567,
        "deliveryPct": 50.00
      }
    ],
    "pageNumber": 0,
    "pageSize": 50,
    "totalElements": 123,
    "totalPages": 3
  }
}
```

---

### 3️⃣ Get Top Gainers for a Date
```
GET /api/v1/stocks/top-gainers?date=15-01-2025&limit=5
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": {
    "stocks": [
      {
        "symbol": "AXISBANK",
        "closePrice": 1110.00,
        "pctChange": 2.30
      },
      {
        "symbol": "ICICIBANK",
        "closePrice": 1000.00,
        "pctChange": 2.04
      }
    ],
    "tradeDate": "2025-01-15",
    "category": "GAINERS",
    "count": 5
  }
}
```

---

### 4️⃣ Get Stock Detail
```
GET /api/v1/stocks/INFY?date=15-01-2025
```

**Response:**
```json
{
  "status": "SUCCESS",
  "data": {
    "symbol": "INFY",
    "company_name": "Infosys Limited",
    "sector": "Information Technology",
    "tradeDate": "2025-01-15",
    "openPrice": 1845.50,
    "highPrice": 1865.25,
    "lowPrice": 1840.00,
    "closePrice": 1860.00,
    "prevClose": 1850.00,
    "pctChange": 0.54,
    "tradedQuantity": 5234567,
    "turnover": 9850000000.00,
    "deliveryQty": 2617283,
    "deliveryPct": 50.00
  }
}
```

---

## 📅 Date Format Reference

**Important:** All date parameters in Module 2 API use `dd-MM-yyyy` format!

Examples:
- January 15, 2025 → `15-01-2025`
- December 31, 2025 → `31-12-2025`
- March 7, 2026 → `07-03-2026`

**Note:** Module 1 (Data Ingestion) uses `yyyy-MM-dd` format:
- January 15, 2025 → `2025-01-15`

---

## 🧪 Testing Workflow

### Recommended Test Sequence:

1. **Check Available Dates**
   ```
   GET /api/v1/ingestion/available-dates
   ```
   → Note the available dates

2. **Download Data**
   ```
   POST /api/v1/ingestion/download
   Body: { "tradeDate": "2025-01-15", "overwrite": false }
   ```

3. **Verify Download**
   ```
   GET /api/v1/ingestion/status/2025-01-15
   ```

4. **Search Stocks**
   ```
   GET /api/v1/stocks?date=15-01-2025&page=0&size=10
   ```

5. **Get Top Gainers**
   ```
   GET /api/v1/stocks/top-gainers?date=15-01-2025&limit=10
   ```

6. **Get Top Losers**
   ```
   GET /api/v1/stocks/top-losers?date=15-01-2025&limit=10
   ```

---

## 🔒 Request/Response Headers

### Request Headers (Auto-set in Collection)
```
Content-Type: application/json
Accept: application/json
```

### Response Headers
```
Content-Type: application/json
Server: Apache-Coyote/1.1
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, must-revalidate
```

---

## ⚠️ Error Responses

### 400 Bad Request
```json
{
  "status": "ERROR",
  "message": "Invalid date format: 15/01/2025. Please use dd-MM-yyyy (e.g., 15-01-2025)",
  "data": null
}
```

### 404 Not Found
```json
{
  "status": "ERROR",
  "message": "Stock 'XYZ' not found for date: 2025-01-15",
  "data": null
}
```

### 409 Conflict
```json
{
  "status": "ERROR",
  "message": "Data for 2025-01-15 already exists (1250 records). Set overwrite=true to replace.",
  "data": null
}
```

### 500 Server Error
```json
{
  "status": "ERROR",
  "message": "An unexpected error occurred. Please contact support.",
  "data": null
}
```

---

## 💡 Tips & Tricks

### 1. Use Postman Variables
Instead of hardcoding values, use variables:
```
{{baseUrl}} → http://localhost:8080
{{tradeDate}} → 2025-01-15
```

Edit variables: **Variables** tab at top

### 2. Pre-request Scripts
Add scripts to validate/transform request data before sending:
```javascript
// Example: Validate date format
const date = pm.variables.get("tradeDate");
if (!date.match(/^\d{4}-\d{2}-\d{2}$/)) {
    throw new Error("Invalid date format!");
}
```

### 3. Tests (Response Validation)
Add tests to verify response:
```javascript
pm.test("Status is SUCCESS", function() {
    var jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql("SUCCESS");
});

pm.test("Response has data", function() {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.exist;
});
```

### 4. Collection Runner
Run multiple requests in sequence:
1. Click **Collection** → **Run**
2. Select requests to run
3. Choose delay between requests
4. Click **Run**

### 5. Export Response as Example
Save API responses as examples for documentation:
1. Send request
2. Click **Save as Example** in response
3. Use in API documentation

---

## 🚀 Performance Optimization

### Pagination Best Practices
```
GET /api/v1/stocks?page=0&size=100
```
- Use `size=100` for large result sets
- Avoid fetching all records at once
- Use `sortBy` and `sortDir` for ordering

### Filter Optimization
```
GET /api/v1/stocks?date=15-01-2025&minPrice=100&maxPrice=1000
```
- Use specific filters to reduce result set
- Date is always required for best performance
- Combine multiple filters for precise results

### Caching
Results are cached for 1 hour by default:
- Use `Cache-Control` header to control caching
- Clear cache if data is updated

---

## 📞 Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection refused | Ensure application is running on `localhost:8080` |
| Invalid date format | Use correct format: Module 1: `yyyy-MM-dd`, Module 2: `dd-MM-yyyy` |
| 404 Not Found | Check endpoint URL spelling and HTTP method |
| No data returned | Download data first using POST `/api/v1/ingestion/download` |
| CORS error | API is configured with `@CrossOrigin("*")` - should work from any origin |
| Authentication error | No authentication required for this API |

---

## 📦 Collection File Information

- **File:** `NSE_Insights_Postman_Collection.json`
- **Version:** 1.0.0
- **Format:** Postman Collection v2.1.0
- **APIs:** 12 total (6 Module 1 + 6 Module 2)
- **Variables:** 1 (baseUrl)
- **Last Updated:** March 7, 2026

---

## 🎯 Next Steps

1. ✅ Import collection into Postman
2. ✅ Update `baseUrl` if necessary
3. ✅ Ensure application is running
4. ✅ Test endpoints using the recommended workflow
5. ✅ Check responses and debug any issues

---

**Ready to test the APIs!** 🚀
