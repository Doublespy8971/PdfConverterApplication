# DocConvert Pro – Multi-Format PDF & Document Conversion

A production-ready Spring Boot application that converts between PDF and common office formats with asynchronous processing, configurable rate limiting, and optional AI summarization. Deploy locally or via Docker with automatic LibreOffice integration.

**🌐 Live Demo: [docconvertpro.duckdns.org](https://docconvertpro.duckdns.org)**

![Project Status](https://img.shields.io/badge/status-production-green) ![Java Version](https://img.shields.io/badge/java-21%2B-blue) ![License](https://img.shields.io/badge/license-MIT-green)
[![Deploy to Oracle Cloud](https://github.com/Doublespy8971/PdfConverterApplication/actions/workflows/deploy.yml/badge.svg)](https://github.com/Doublespy8971/PdfConverterApplication/actions/workflows/deploy.yml)

**Deployed on:** Oracle Cloud Infrastructure (VM.Standard.A1.Flex, ARM, 6GB RAM) with nginx reverse proxy and Let's Encrypt SSL.

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
| **Hosting** | Oracle Cloud Infrastructure (Always Free Tier) |
| **Web Server** | nginx with Let's Encrypt SSL |

---

<a name="architecture"></a>
## Architecture

### System Overview

┌─────────────────────────────────────────────────────────────────┐
│ Web Browser │
│ (HTML/CSS/JavaScript UI) │
└────────────────────────────┬────────────────────────────────────┘
│ HTTPS
▼
┌─────────────────┐
│ nginx reverse │
│ proxy + SSL │
└────────┬────────┘
│
▼
┌─────────────────────────────────────────────────────────────────┐
│ Spring Boot REST API (Port 8080) │
├─────────────────────────────────────────────────────────────────┤
│ Controllers │
│ ├─ ConverterController (POST /api/convert/, GET /api/convert/) │
│ └─ AIController (POST /api/ai/summarize) │
├─────────────────────────────────────────────────────────────────┤
│ Security & Interceptors │
│ ├─ RateLimitingInterceptor (15 req/hour per IP) │
│ └─ SecurityConfig (CORS, CSRF, CSP headers) │
├─────────────────────────────────────────────────────────────────┤
│ Services │
│ ├─ TaskRegistryService (task state, in-memory ConcurrentHashMap) │
│ ├─ AsyncConversionWorker (ThreadPoolExecutor 4-8 threads) │
│ ├─ ConversionService (11 conversion implementations) │
│ ├─ LibreOfficeConverterService (subprocess management) │
│ └─ LLMProvider (interface for AI providers) │
│ ├─ OpenAIProvider (GPT-3.5) │
│ └─ GeminiProvider (Google Gemini, future) │
└─────┬──────────────────────────┬──────────────────────┬─────────┘
│ │ │
▼ ▼ ▼
┌────────────┐ ┌────────────────┐ ┌──────────────┐
│ PDFBox │ │ LibreOffice │ │ OpenAI API │
│(PDF ops) │ │ (subprocess) │ │ (AI Summary) │
└────────────┘ └────────────────┘ └──────────────┘

File Storage:
├─ Uploads: $JAVA_TMPDIR/convert_<taskId>/ (temporary)
├─ Results: JVM heap (byte arrays, up to 100MB each)
└─ Cleanup: Automatic hourly; tasks expire after TTL


### Request Lifecycle
POST /api/convert/word-to-pdf with file upload
↓
ConverterController validates and streams file to temp directory
↓
Generate UUID taskId and initiate task in TaskRegistryService
↓
Submit async job to AsyncConversionWorker thread pool
↓
HTTP 202 Accepted response with taskId
↓
[Async thread pool processes in background]
├─ Update status: PENDING → PROCESSING
├─ LibreOfficeConverterService spawns: soffice --headless --convert-to pdf
├─ Read resulting PDF bytes
├─ Store in TaskRegistryService result cache
└─ Update status: PROCESSING → COMPLETED/FAILED
↓
Client polls GET /api/convert/status/{taskId} every 2 seconds
↓
Once COMPLETED → GET /api/convert/download/{taskId}

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
# Build and start (includes LibreOffice)
docker-compose up -d

# View logs
docker-compose logs -f pdf-converter

# Access at: http://localhost:8080
```

### Quick Test

```bash
# Submit conversion
curl -X POST \
  -F "file=@document.docx" \
  http://localhost:8080/api/convert/word-to-pdf

# Poll status
curl http://localhost:8080/api/convert/status/{taskId}

# Download result
curl -O http://localhost:8080/api/convert/download/{taskId}
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
  "status": "PENDING",
  "message": "Conversion processing initiated"
}
```

**Error Responses:**
- `400 Bad Request`: Empty file or unsupported extension
- `429 Too Many Requests`: Rate limit exceeded
- `413 Payload Too Large`: File exceeds 100MB

---

#### POST `/api/convert/batch/{tool}`

Convert multiple files. Results packaged as ZIP.

**Response (HTTP 202):**
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Batch conversion processing initiated"
}
```

---

#### GET `/api/convert/status/{taskId}`

Poll task status.

**Response (HTTP 200):**
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "fileName": "document.pdf",
  "contentType": "application/pdf",
  "createdAt": 1692374800000,
  "updatedAt": 1692374805000,
  "resultSize": 234567,
  "errorMessage": null
}
```

**Status values:** `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`

---

#### GET `/api/convert/download/{taskId}`

Download completed result. Task removed from registry after download.

**Response:**
- `200 OK`: File binary data
- `202 Accepted`: Still processing
- `400 Bad Request`: Conversion failed
- `404 Not Found`: Task not found or expired

---

#### GET `/api/convert/metrics`

Get task statistics.

**Response (HTTP 200):**
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

#### POST `/api/ai/summarize`

Summarize a PDF using OpenAI (requires API key).

**Parameters:**
- `file` (form): PDF file
- `length` (form): `short`, `medium`, or `long`

---

<a name="configuration"></a>
## Configuration

### application.properties

```properties
# Server
server.port=8080

# File Upload Limits
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=500MB

# CORS
app.cors.allowed-origin=http://localhost:8080

# AI Summarization (optional)
ai.provider=openai
openai.api.key=sk-your-openai-key
openai.model=gpt-3.5-turbo

# Rate Limiting
app.rate-limit.trust-forwarded-headers=false
app.rate-limit.trusted-proxies=

# Async Processing
app.async.core-pool-size=4
app.async.max-pool-size=8
app.async.queue-capacity=100

# Task Cleanup
app.tasks.completed-retention-hours=2
app.tasks.processing-timeout-hours=6
app.tasks.cleanup-interval-ms=3600000
```

### Environment Variables

```bash
JAVA_OPTS="-Xmx512m -Xms256m"
OPENAI_API_KEY=sk-your-key
APP_CORS_ALLOWED_ORIGIN=https://yourdomain.com
SERVER_PORT=8080
```

### Production Nginx Configuration

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 100m;
    }
}
```

---

<a name="known-limitations"></a>
## Known Limitations

### Single-Instance Only
- Task state stored in-memory; tasks lost on restart
- Not horizontally scalable without Redis/database
- Planned fix: Redis task backend (v1.1)

### LibreOffice Resource Constraints
- Each office conversion spawns ~500MB process
- Limited to 2 concurrent LibreOffice conversions
- Planned fix: RabbitMQ job queue (v2.0)

### PDF Conversion Quality
- Text extraction only; layout not preserved
- PDF → Word/Excel/PPT conversions have limited fidelity

### Memory Constraints
- Default 512MB heap supports ~5 concurrent 100MB conversions
- Increase `-Xmx` for production workloads

### File Size Limits
- Max 100MB per file
- Max 500MB per batch request

---

<a name="roadmap"></a>
## Roadmap

### v1.1 (Q3 2026)
- [ ] Redis task backend for horizontal scaling
- [ ] Enhanced error logging and monitoring
- [ ] API request authentication

### v2.0 (Q4 2026)
- [ ] RabbitMQ job queue for reliability
- [ ] S3 storage backend for results
- [ ] WebSocket real-time progress updates
- [ ] OCR support (Tesseract integration)
- [ ] Advanced PDF operations (form filling, digital signatures)

---

## Performance Benchmarks

Measured on Oracle Cloud VM.Standard.A1.Flex (1 OCPU, 6GB RAM):

| Operation | Duration | Notes |
|-----------|----------|-------|
| DOCX → PDF | 2-5 sec | LibreOffice startup overhead |
| PDF → Images (10 pages) | 3-8 sec | 150 DPI rasterization |
| Merge 5 PDFs | 1-2 sec | Fast PDFBox operations |
| Compress PDF (10MB) | 2-4 sec | Image re-encoding |
| Images → PDF (10 images) | 1-3 sec | Pure Java; no subprocess |

---

## Troubleshooting

### LibreOffice Not Found
```bash
brew install libreoffice        # macOS
sudo apt-get install libreoffice # Ubuntu
# Or use: docker-compose up -d
```

### Port 8080 Already in Use
```bash
lsof -i :8080
kill -9 <PID>
```

### Out of Memory Error
```bash
export JAVA_OPTS="-Xmx2g -Xms1g"
mvn spring-boot:run
```

### Rate Limit Blocking Traffic
```properties
app.rate-limit.trust-forwarded-headers=true
app.rate-limit.trusted-proxies=10.0.0.0/8
```

---

## Built With

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue?logo=java)](https://www.oracle.com/java/technologies/downloads/)
[![Apache PDFBox](https://img.shields.io/badge/PDFBox-2.0.33-red?logo=apache)](https://pdfbox.apache.org/)
[![LibreOffice](https://img.shields.io/badge/LibreOffice-7.x-blue?logo=libreoffice)](https://www.libreoffice.org/)
[![Docker](https://img.shields.io/badge/Docker-20.10+-2496ED?logo=docker)](https://www.docker.com/)
[![Oracle Cloud](https://img.shields.io/badge/Oracle_Cloud-Free_Tier-red?logo=oracle)](https://www.oracle.com/cloud/free/)
[![nginx](https://img.shields.io/badge/nginx-reverse_proxy-green?logo=nginx)](https://nginx.org/)
[![Let's Encrypt](https://img.shields.io/badge/SSL-Let's_Encrypt-blue)](https://letsencrypt.org/)

---

## License

This project is provided as-is for educational and internal use.

---

**Last Updated:** September 2026 | **Status:** Production Ready | **Hosted:** Oracle Cloud Free Tier
