@echo off
REM PDF Converter Application - Docker Setup Script for Windows
REM This script sets up and runs the PDF Converter on any Windows machine with Docker

setlocal enabledelayedexpansion

echo.
echo 🚀 PDF Converter Application - Docker Setup
echo ===========================================
echo.

REM Check if Docker is installed
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not installed.
    echo Please install Docker Desktop from: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

echo ✅ Docker is installed

REM Check if Docker daemon is running
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker daemon is not running.
    echo Please start Docker Desktop and try again.
    pause
    exit /b 1
)

echo ✅ Docker daemon is running
echo.

REM Build the image
echo 🔨 Building Docker image (this may take 3-5 minutes on first run)...
docker-compose build

echo.
echo ✅ Docker image built successfully
echo.

REM Start the application
echo 🚀 Starting PDF Converter Application...
docker-compose up -d

echo.
echo ✅ Application started successfully!
echo.

echo ⏳ Waiting for application to be ready...
timeout /t 10 /nobreak

echo.
echo ✅ PDF Converter is running!
echo.
echo 📍 Application URL: http://localhost:8080
echo.
echo Available Tools:
echo   ✓ Word to PDF
echo   ✓ Excel to PDF
echo   ✓ PowerPoint to PDF
echo   ✓ Images to PDF
echo   ✓ PDF to Images
echo   ✓ PDF to Word
echo   ✓ PDF to Excel
echo   ✓ PDF to PowerPoint
echo   ✓ Split PDF
echo   ✓ Merge PDF
echo   ✓ Compress PDF
echo   ✓ AI Summarizer (optional)
echo.
echo 📝 Useful Commands:
echo   View logs:     docker-compose logs -f
echo   Stop app:      docker-compose down
echo   Restart:       docker-compose restart
echo.
pause

