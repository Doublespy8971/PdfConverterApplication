#!/bin/bash

# PDF Converter Application - Docker Setup Script
# This script sets up and runs the PDF Converter on any machine with Docker

set -e

echo "🚀 PDF Converter Application - Docker Setup"
echo "==========================================="
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed."
    echo "Please install Docker from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed."
    echo "Please install Docker Compose from: https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✅ Docker is installed"
echo "✅ Docker Compose is installed"
echo ""

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo "❌ Docker daemon is not running."
    echo "Please start Docker and try again."
    exit 1
fi

echo "✅ Docker daemon is running"
echo ""

# Build the image
echo "🔨 Building Docker image (this may take 3-5 minutes on first run)..."
docker-compose build

echo ""
echo "✅ Docker image built successfully"
echo ""

# Start the application
echo "🚀 Starting PDF Converter Application..."
docker-compose up -d

echo ""
echo "✅ Application started successfully!"
echo ""

# Wait for application to be ready
echo "⏳ Waiting for application to be ready..."
sleep 10

# Check if application is running
if docker-compose ps | grep -q "pdf-converter"; then
    echo "✅ PDF Converter is running!"
    echo ""
    echo "📍 Application URL: http://localhost:8080"
    echo ""
    echo "Available Tools:"
    echo "  ✓ Word to PDF"
    echo "  ✓ Excel to PDF"
    echo "  ✓ PowerPoint to PDF"
    echo "  ✓ Images to PDF"
    echo "  ✓ PDF to Images"
    echo "  ✓ PDF to Word"
    echo "  ✓ PDF to Excel"
    echo "  ✓ PDF to PowerPoint"
    echo "  ✓ Split PDF"
    echo "  ✓ Merge PDF"
    echo "  ✓ Compress PDF"
    echo "  ✓ AI Summarizer (optional)"
    echo ""
    echo "📝 Useful Commands:"
    echo "  View logs:     docker-compose logs -f"
    echo "  Stop app:      docker-compose down"
    echo "  Restart:       docker-compose restart"
    echo ""
else
    echo "❌ Failed to start application"
    echo ""
    echo "Debugging: Run 'docker-compose logs' to see error messages"
    exit 1
fi

