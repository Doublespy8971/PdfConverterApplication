# DocConvert Pro – Multi-Format PDF & Document Conversion
A production-ready Spring Boot application that converts between PDF and common office formats with asynchronous processing, configurable rate limiting, and optional AI summarization. Deploy locally or via Docker with automatic LibreOffice integration.
![Project Status](https://img.shields.io/badge/status-production-green) ![Java Version](https://img.shields.io/badge/java-21%2B-blue) ![License](https://img.shields.io/badge/license-MIT-green)
---
## Navigation
- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)
---
<a name="features"></a>
## Features
### 11+ Conversion Tools
| Tool | Input Formats | Output | Technical Notes |
|------|---------------|--------|-----------------|
| Word to PDF | DOC, DOCX, ODT, RTF, TXT | PDF | Requires LibreOffice |
| Excel to PDF | XLS, XLSX, ODS, CSV | PDF | Requires LibreOffice |
| PowerPoint to PDF | PPT, PPTX, ODP | PDF | Requires LibreOffice |
| Images to PDF | PNG, JPG, GIF, BMP, WebP | PDF | Pure Java; preserves aspect ratio |
| PDF to Images | PDF | ZIP (PNG pages) | Rasterizes at 150 DPI |
| PDF to Word | PDF | DOCX | Text extraction; layout not preserved |
| PDF to Excel | PDF | XLSX | Text-based; limited to 10 sheets |
| PDF to PowerPoint | PDF | PPTX | Text-based; limited to 20 slides |
| Split PDF | PDF | ZIP (individual pages) | One file per page |
| Merge PDF | Multiple PDFs | PDF | Concatenates in order |
| Compress PDF | PDF | PDF | Reduces image resolution to 75% quality |
| AI Summarizer (optional) | PDF | JSON summary | Uses OpenAI API; configurable length |
### Core Features
- **Asynchronous Processing**: HTTP 202 response on submission; clients poll for completion
- **Rate Limiting**: Token bucket algorithm; 15 requests/hour per IP (configurable)
- **Task Registry**: In-memory task storage with auto-expiration; 2-hour retention for results
- **Batch Operations**: Convert multiple files in one request; results packaged as ZIP
- **Responsive Web UI**: Modern single-page interface with progress bars and real-time feedback
- **RESTful API**: Complete API for programmatic use; all endpoints documented
- **Memory Optimized**: Streaming file uploads and downloads; no file buffering
- **Security Hardened**: CORS origin validation, CSRF protection, file type validation, size limits
### Tech Stack
| Component | Technology |
|-----------|-----------|
| **Backend** | Java 21, Spring Boot 3.2.4 |
| **Frontend** | HTML5, CSS3, Vanilla JavaScript (no frameworks) |
| **PDF Processing** | Apache PDFBox 2.0.33 |
| **Office Conversion** | LibreOffice 7.x (subprocess) |
| **Rate Limiting** | Bucket4j 7.6.0 (token bucket) |
| **Caching** | Caffeine 3.1.8 |
| **AI Summarization** | OpenAI GPT-3.5 via OkHttp 4.11.0 |
| **Containerization** | Docker & Docker Compose |
| **Image Processing** | imgscalr 4.2 + Java ImageIO |
| **Office I/O** | Apache POI 5.2.5 |
---
<a name="architecture"></a>
## Architecture
### System Overview
```
┌─────────────────────────────────────────────────────────────────┐
│                         Web Browser                              │
│                    (HTML/CSS/JavaScript UI)                      │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Spring Boot REST API (Port 8080)                │
├─────────────────────────────────────────────────────────────────┤
│  Controllers                                                      │
│  ├─ ConverterController (POST /api/convert/*, GET /api/convert/*) │
│  └─ AIController (POST /api/ai/summarize)                       │
├─────────────────────────────────────────────────────────────────┤
│  Security & Interceptors                                         │
│  ├─ RateLimitingInterceptor (15 req/hour per IP)                │
│  └─ SecurityConfig (CORS, CSRF, CSP headers)                    │
├─────────────────────────────────────────────────────────────────┤
│  Services                                                        │
│  ├─ TaskRegistryService (task state, in-memory ConcurrentHashMap) │
│  ├─ AsyncConversionWorker (ThreadPoolExecutor 4-8 threads)      │
│  ├─ ConversionService (11 conversion implementations)           │
│  ├─ LibreOfficeConverterService (subprocess management)         │
│  └─ LLMProvider (interface for AI providers)                    │
│     ├─ OpenAIProvider (GPT-3.5)                                │
│     └─ GeminiProvider (Google Gemini, future)                  │
└─────┬──────────────────────────┬──────────────────────┬─────────┘
      │                          │                      │
      ▼                          ▼                      ▼
 ┌────────────┐         ┌────────────────┐    ┌──────────────┐
 │  PDFBox    │         │ LibreOffice    │    │  OpenAI API  │
 │(PDF ops)   │         │ (subprocess)   │    │ (AI Summary) │
 └────────────┘         └────────────────┘    └──────────────┘
File Storage:
├─ Uploads: $JAVA_TMPDIR/convert_<taskId>/ (temporary)
├─ Results: JVM heap (byte arrays, up to 100MB each)
└─ Cleanup: Automatic hourly; tasks expire after TTL
```
---
<a name="getting-started"></a>
## Getting Started
### Prerequisites
**For Local Installation:**
- Java 21 or higher
- Maven 3.9+
- LibreOffice 7.x (for office conversions)
- 2GB RAM minimum
- 500MB free disk space
**For Docker:**
- Docker 20.10+
- Docker Compose 2.0+
- 1GB RAM, 1.5GB disk space
### Installation
#### Option 1: Local Development
```bash
# 1. Clone and navigate to project
cd PdfConverterApplication
# 2. Install LibreOffice
# macOS:
brew install libreoffice
# Ubuntu/Debian:
sudo apt-get install libreoffice
# 3. Build
mvn clean package -DskipTests
# 4. Run
mvn spring-boot:run
# 5. Access at: http://localhost:8080
```
#### Option 2: Docker & Docker Compose
```bash
# Build and start
docker-compose up -d
# Access at: http://localhost:8080
```
---
<a name="api-reference"></a>
## API Reference
### Conversion Endpoints
#### POST `/api/convert/{tool}`
Convert a single file asynchronously. Returns HTTP 202 with taskId.
**Response (HTTP 202):**
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```
#### GET `/api/convert/status/{taskId}`
Poll task status.
**Response (HTTP 200):**
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "fileName": "document.pdf",
  "contentType": "application/pdf",
  "resultSize": 234567
}
```
#### GET `/api/convert/download/{taskId}`
Download completed result. Returns file binary data.
#### GET `/api/convert/metrics`
Get task statistics.
**Response:**
```json
{
  "totalTasks": 42,
  "pendingTasks": 2,
  "processingTasks": 1,
  "completedTasks": 35,
  "failedTasks": 4
}
```
---
<a name="configuration"></a>
## Configuration
### application.properties
```properties
server.port=8080
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=500MB
app.cors.allowed-origin=http://localhost:8080
openai.api.key=sk-your-openai-key
openai.model=gpt-3.5-turbo
```
### Environment Variables (Docker)
```bash
JAVA_OPTS="-Xmx512m -Xms256m"
OPENAI_API_KEY=sk-your-key
SERVER_PORT=8080
```
---
<a name="known-limitations"></a>
## Known Limitations
### Single-Instance Only
- Task state stored in-memory; tasks lost on restart
- Not horizontally scalable without Redis/database
### LibreOffice Resource Constraints
- Limited to 2 concurrent conversions
- Each conversion requires ~500MB
### PDF Conversion Quality
- Text extraction only; layout not preserved
- Limited fidelity in PDF→Word/Excel/PPT conversions
### Memory Constraints
- Default 512MB heap supports ~5 concurrent 100MB conversions
- Use `-Xmx2g -Xms1g` for production
### File Size Limits
- Max 100MB per file
- Max 500MB per request
---
<a name="roadmap"></a>
## Roadmap
### v1.1 (Q3 2026)
- Redis task backend
- Enhanced error logging
- API request authentication
### v2.0 (Q4 2026)
- RabbitMQ job queue
- S3 storage backend
- WebSocket real-time updates
---
## Troubleshooting
### LibreOffice Not Found
```bash
brew install libreoffice
# Or use: docker-compose up -d
```
### Port 8080 Already in Use
```bash
lsof -i :8080
kill -9 <PID>
# Or change server.port in application.properties
```
### Out of Memory Error
```bash
export JAVA_OPTS="-Xmx2g -Xms1g"
mvn spring-boot:run
```
---
## Built With
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue?logo=java)](https://www.oracle.com/java/technologies/downloads/)
[![Apache PDFBox](https://img.shields.io/badge/PDFBox-2.0.33-red?logo=apache)](https://pdfbox.apache.org/)
[![LibreOffice](https://img.shields.io/badge/LibreOffice-7.x-blue?logo=libreoffice)](https://www.libreoffice.org/)
[![Docker](https://img.shields.io/badge/Docker-20.10+-2496ED?logo=docker)](https://www.docker.com/)
---
## License
This project is provided as-is for educational and internal use.
---
**Last Updated:** May 2026 | **Status:** Production Ready
For detailed error messages, check application logs:
```bash
mvn spring-boot:run 2>&1 | grep -i error
docker-compose logs pdf-converter --tail=100
```
Monitor application health: GET http://localhost:8080/api/convert/metrics
