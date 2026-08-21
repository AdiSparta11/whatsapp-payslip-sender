@echo off
title WhatsApp Payslip Dispatcher - M/S. FRIENDS ENTERPRISE
color 0A

echo =================================================================
echo        WhatsApp Payslip Dispatcher - Spring Boot Web App
echo =================================================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH!
    echo Please install JDK 17 or higher from https://adoptium.net/
    echo.
    pause
    exit /b 1
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not in PATH!
    echo Please install Apache Maven from https://maven.apache.org/
    echo.
    pause
    exit /b 1
)

echo Starting Spring Boot Web Application on http://localhost:8080 ...
echo Press Ctrl+C in this window to stop the server.
echo.

start "" "http://localhost:8080"

call mvn spring-boot:run
pause
