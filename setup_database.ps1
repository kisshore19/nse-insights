# ============================================================
# NSE Insights - MySQL Database Setup Script (PowerShell)
# ============================================================
#
# This script sets up the MySQL database for NSE Insights
# Make sure MySQL is installed and running before executing
#
# Usage:
#   1. Open PowerShell as Administrator
#   2. Run: Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
#   3. Run: .\setup_database.ps1
# ============================================================

# Configuration Variables
$MYSQL_PATH = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$DB_USER = "root"
$DB_PASS = "root"
$PROJECT_PATH = "E:\StockResearch\nse-insights"
$SQL_FILE = "$PROJECT_PATH\DATABASE_SETUP.sql"

# Color functions
function Write-Info {
    Write-Host "[INFO] $args" -ForegroundColor Cyan
}

function Write-Success {
    Write-Host "[SUCCESS] $args" -ForegroundColor Green
}

function Write-Error {
    Write-Host "[ERROR] $args" -ForegroundColor Red
}

function Write-Warning {
    Write-Host "[WARNING] $args" -ForegroundColor Yellow
}

# Main script
Clear-Host
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  NSE Insights Database Setup" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Check if MySQL path exists
$mysqlExe = Join-Path $MYSQL_PATH "mysql.exe"
if (-not (Test-Path $mysqlExe)) {
    Write-Error "MySQL not found at: $MYSQL_PATH"
    Write-Info "Please update MYSQL_PATH variable in the script"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Info "MySQL found at: $MYSQL_PATH"
Write-Info "Database user: $DB_USER"
Write-Info "SQL script: $SQL_FILE"
Write-Host ""

# Check if SQL file exists
if (-not (Test-Path $SQL_FILE)) {
    Write-Error "SQL file not found: $SQL_FILE"
    Write-Info "Please ensure DATABASE_SETUP.sql exists in the project root"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Info "SQL file found"
Write-Host ""
Write-Info "Executing database setup..."
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Execute the SQL script
try {
    $sqlContent = Get-Content $SQL_FILE -Raw
    $process = Start-Process -FilePath $mysqlExe -ArgumentList "-u $DB_USER -p$DB_PASS" -NoNewWindow -RedirectStandardInput $true -Wait

    # Alternative method using pipe
    $sqlContent | & $mysqlExe -u $DB_USER -p$DB_PASS

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Success "Database setup completed successfully!"
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""

    Write-Info "Next steps:"
    Write-Host "1. Verify database setup by running:"
    Write-Host "   & '$mysqlExe' -u $DB_USER -p$DB_PASS -e 'USE nse_insights; SHOW TABLES; SELECT COUNT(*) as records FROM nse_daily_price;'"
    Write-Host ""
    Write-Host "2. Update application.properties with database credentials"
    Write-Host ""
    Write-Host "3. Start the Spring Boot application:"
    Write-Host "   cd $PROJECT_PATH"
    Write-Host "   gradle bootRun"
    Write-Host ""

}
catch {
    Write-Error "Database setup failed: $_"
    Write-Info "Please check the error messages above"
}

Read-Host "Press Enter to exit"
