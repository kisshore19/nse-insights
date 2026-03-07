@echo off
REM ============================================================
REM NSE Insights - MySQL Database Setup Script (Windows)
REM ============================================================
REM
REM This script sets up the MySQL database for NSE Insights
REM Make sure MySQL is installed and running before executing
REM
REM Usage:
REM   1. Edit the variables below (MYSQL_PATH, DB_USER, DB_PASS)
REM   2. Save this file
REM   3. Double-click to run, or run from command prompt
REM ============================================================

REM Configuration Variables
SET MYSQL_PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin
SET DB_USER=root
SET DB_PASS=root
SET PROJECT_PATH=E:\StockResearch\nse-insights
SET SQL_FILE=%PROJECT_PATH%\DATABASE_SETUP.sql

REM Colors for output
setlocal enabledelayedexpansion

echo.
echo ============================================================
echo  NSE Insights Database Setup
echo ============================================================
echo.

REM Check if MySQL path exists
if not exist "%MYSQL_PATH%\mysql.exe" (
    echo [ERROR] MySQL not found at: %MYSQL_PATH%
    echo Please update MYSQL_PATH variable
    pause
    exit /b 1
)

echo [INFO] MySQL found at: %MYSQL_PATH%
echo [INFO] Database user: %DB_USER%
echo [INFO] SQL script: %SQL_FILE%
echo.

REM Check if SQL file exists
if not exist "%SQL_FILE%" (
    echo [ERROR] SQL file not found: %SQL_FILE%
    echo Please ensure DATABASE_SETUP.sql exists in the project root
    pause
    exit /b 1
)

echo [INFO] SQL file found
echo.
echo [INFO] Executing database setup...
echo ============================================================
echo.

REM Execute the SQL script
"%MYSQL_PATH%\mysql.exe" -u %DB_USER% -p%DB_PASS% < "%SQL_FILE%"

if errorlevel 1 (
    echo.
    echo [ERROR] Database setup failed!
    echo Please check the error messages above
    pause
    exit /b 1
)

echo.
echo ============================================================
echo [SUCCESS] Database setup completed successfully!
echo ============================================================
echo.
echo Next steps:
echo 1. Verify database setup by running:
echo    %MYSQL_PATH%\mysql.exe -u %DB_USER% -p%DB_PASS% -e "USE nse_insights; SHOW TABLES; SELECT COUNT(*) as records FROM nse_daily_price;"
echo.
echo 2. Update application.properties with database credentials
echo.
echo 3. Start the Spring Boot application:
echo    cd %PROJECT_PATH%
echo    gradle bootRun
echo.
pause
