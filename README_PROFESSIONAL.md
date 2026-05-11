# PDF Converter Application

A Spring Boot-based document conversion service supporting 11 conversion tools, asynchronous processing, and optional AI-powered PDF summarization.

## Overview

This application provides a REST API and web interface for converting between PDF and common office formats (Word, Excel, PowerPoint), converting images to/from PDF, and performing PDF operations (merge, split, compress). Conversions are processed asynchronously to prevent HTTP timeouts on long-running operations.

**Current Status:** Functional for single-instance deployments and development use. Memory-based task storage and LibreOffice resource requirements limit horizontal scalability.

## Architecture

```
┌─────────────────┐
│   Web Browser   │
│  (HTML/JS UI)   │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────────────────────────┐
│      Spring Boot REST API           │
│  (Port 8080)                        │
├─────────────────────────────────────┤
│  Controllers                        │
│  ├─ ConverterController             │
│  └─ AIController                    │
├─────────────────────────────────────┤
│  Services                           │
│  ├─ ConversionService               │
│  ├─ LibreOfficeConverterService     │
│  ├─ TaskRegistryService (in-memory) │
│  ├─ AsyncConversionWorker           │
│  └─ LLMProvider (OpenAI/Gemini)     │
├─────────────────────────────────────┤
│  Rate Limiting (15 req/hour per IP) │
└────────┬────────────────────────────┘
         │
    ┌────┴─────────────────────────┐
    ▼                              ▼
┌─────────────────┐      ┌─────────────────┐
│   PDFBox        │      │  LibreOffice    │
│ (PDF ops)       │      │ (Office → PDF)  │
└─────────────────┘      └─────────────────┘
```

### Key Components

**ConverterController**
- Accepts file uploads and initiates async conversions
- Returns HTTP 202 (Accepted) with task ID, not the result
- Provides status polling and result download endpoints
- Supports batch operations via `/batch/{tool}` endpoint

**ConversionService**
- Implements 11 conversion tools
- Uses PDFBox for PDF operations (free, pure Java)
- Delegates office format conversions to LibreOffice via subprocess calls
- Handles file validation and temporary file cleanup

**TaskRegistryService**
- In-memory concurrent map of task status objects
- Each task stores up to 100MB of converted data in heap
- Expires tasks after configurable TTL (default: 2 hours for completed, 6 hours for processing)
- Single-instance only; tasks lost on restart

**AsyncConversionWorker**
- Thread pool executor (4-8 threads by default, configurable)
- Processes conversions in background
- Updates task status: PENDING → PROCESSING → COMPLETED/FAILED

**LibreOfficeConverterService**
- Spawns headless LibreOffice process via subprocess
- Converts office documents to PDF using UNO bridge
- Single conversion per process; resource-intensive
- Requires ~500MB per conversion instance

**Rate Limiting (RateLimitingInterceptor)**
- Token bucket algorithm via Bucket4j
- 15 requests/hour per IP address
- Configurable to trust `X-Forwarded-For` when behind proxies

### File Storage

- Uploaded files: `$JAVA_TMPDIR/convert_<taskId>/` (temporary storage)
- Results: Stored in-memory as byte arrays in `TaskRegistryService`
- Cleanup: Automatic deletion of temp files after conversion; tasks expire after TTL

## Supported Conversions

| Tool | Input Formats | Output | Notes |
|------|---------------|--------|-------|
| Word to PDF | DOC, DOCX, ODT, RTF, TXT | PDF | Requires LibreOffice |
| Excel to PDF | XLS, XLSX, ODS, CSV | PDF | Requires LibreOffice |
| PowerPoint to PDF | PPT, PPTX, ODP | PDF | Requires LibreOffice |
| Images to PDF | PNG, JPG, JPEG, GIF, BMP, WebP | PDF | Pure Java; preserves aspect ratio |
| PDF to Images | PDF | ZIP (PNG pages) | Rasterizes at 150 DPI |
| PDF to Word | PDF | DOCX | Text extraction only; no layout preservation |
| PDF to Excel | PDF | XLSX | Text-based; limit 10 sheets |
| PDF to PowerPoint | PDF | PPTX | Text-based; limit 20 slides |
| Split PDF | PDF | ZIP (individual pages) | One file per page |
| Merge PDF | Multiple PDFs | PDF | Concatenates in order; no deduplication |
| Compress PDF | PDF | PDF | Reduces image resolution; quality 0.75 |

## Setup

### Prerequisites

- Java 21+
- Maven 3.9+ (for local builds)
- LibreOffice (for office conversions; optional if using only PDF ops)
- 2GB RAM minimum (more if handling large files concurrently)

### Local Installation

1. **Clone and build:**
   ```bash
   cd PdfConverterApplication-main
   mvn clean package -DskipTests
   ```

2. **Install LibreOffice (optional, required for office→PDF conversions):**
   ```bash
   # macOS
   brew install libreoffice
   
   # Ubuntu/Debian
   sudo apt-get install libreoffice
   
   # or download from https://www.libreoffice.org
   ```

3. **Configure** (optional, edit `src/main/resources/application.properties`):
   ```properties
   server.port=8080
   spring.servlet.multipart.max-file-size=100MB
   app.cors.allowed-origin=http://localhost:8080
   ai.provider=openai
   openai.api.key=sk-your-key-here
   ```

4. **Run:**
   ```bash
   mvn spring-boot:run
   ```

5. **Access:**
   ```
   http://localhost:8080
   ```

### Docker Installation

1. **Build and start:**
   ```bash
   docker-compose up -d
   ```

2. **Check logs:**
   ```bash
   docker-compose logs -f pdf-converter
   ```

3. **Stop:**
   ```bash
   docker-compose down
   ```

The Docker image includes LibreOffice preinstalled. Default heap size is 512MB; adjust `JAVA_OPTS` in `docker-compose.yml` for larger files.

## Usage

### Web Interface

1. Visit `http://localhost:8080`
2. Select a conversion tool
3. Upload file(s)
4. Click "Convert"
5. Download result when ready

### REST API

All conversion endpoints are async. The flow is:
1. POST file → receive task ID (HTTP 202)
2. GET status by task ID (poll until complete)
3. GET download to fetch result

**Initiate Conversion:**
```bash
curl -X POST \
  -F "file=@document.docx" \
  http://localhost:8080/api/convert/word-to-pdf

# Response (HTTP 202):
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Conversion processing initiated"
}
```

**Check Status:**
```bash
curl http://localhost:8080/api/convert/status/{taskId}

# Response:
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "fileName": "document.pdf",
  "contentType": "application/pdf",
  "createdAt": 1692374800000,
  "updatedAt": 1692374805000,
  "resultSize": 0
}
```

**Download Result:**
```bash
curl -O http://localhost:8080/api/convert/download/{taskId}
```

**Batch Conversion:**
```bash
curl -X POST \
  -F "files=@file1.docx" \
  -F "files=@file2.docx" \
  http://localhost:8080/api/convert/batch/word-to-pdf
```

**Get Metrics:**
```bash
curl http://localhost:8080/api/convert/metrics
```

## Configuration

### application.properties

```properties
# Server
server.port=8080

# File uploads
spring.servlet.multipart.max-file-size=100MB          # Per file
spring.servlet.multipart.max-request-size=500MB       # Per request

# CORS (adjust for production)
app.cors.allowed-origin=http://localhost:8080

# AI Summarization
ai.provider=openai                                      # or "gemini"
openai.api.key=sk-your-openai-key
openai.model=gpt-3.5-turbo

# Rate Limiting
app.rate-limit.trust-forwarded-headers=false           # Set true if behind proxy
app.rate-limit.trusted-proxies=                        # Comma-separated list

# Async Processing
app.async.core-pool-size=4                             # Min threads
app.async.max-pool-size=8                              # Max threads
app.async.queue-capacity=100                           # Pending tasks buffer

# Task Management
app.tasks.completed-retention-hours=2                  # Keep completed tasks in memory
app.tasks.processing-timeout-hours=6                   # Abort stalled tasks
app.tasks.cleanup-interval-ms=3600000                  # Run cleanup every hour
```

### Environment Variables (Docker)

```bash
JAVA_OPTS="-Xmx512m -Xms256m"                          # Heap size
OPENAI_API_KEY=sk-your-key
OPENAI_MODEL=gpt-3.5-turbo
```

## API Reference

### POST `/api/convert/{tool}`

Convert a single file asynchronously.

**Parameters:**
- `tool` (path): One of the 11 supported tools
- `file` (form): Multipart file upload

**Response (HTTP 202):**
```json
{
  "taskId": "string (UUID)",
  "status": "PENDING",
  "message": "string"
}
```

**Errors:**
- `400 Bad Request`: Empty file or unsupported extension
- `400 Bad Request`: LibreOffice not available
- `429 Too Many Requests`: Rate limit exceeded

---

### POST `/api/convert/batch/{tool}`

Batch convert multiple files. Returns a ZIP with folder structure.

**Parameters:**
- `tool` (path): Conversion tool
- `files` (form): Multiple file uploads

**Response (HTTP 202):**
```json
{
  "taskId": "string",
  "status": "PENDING",
  "message": "Batch conversion processing initiated"
}
```

---

### GET `/api/convert/status/{taskId}`

Poll task status without downloading.

**Response (HTTP 200):**
```json
{
  "taskId": "string",
  "status": "PROCESSING|COMPLETED|FAILED|PENDING",
  "fileName": "string",
  "contentType": "string",
  "errorMessage": "string or null",
  "createdAt": 1692374800000,
  "updatedAt": 1692374805000,
  "resultSize": 12345
}
```

**Errors:**
- `404 Not Found`: Task does not exist

---

### GET `/api/convert/download/{taskId}`

Download completed result.

**Response:**
- `200 OK`: File binary data (Content-Disposition: attachment)
- `202 Accepted`: Still processing
- `400 Bad Request`: Conversion failed
- `404 Not Found`: Task not found

**Behavior:** Task is removed from registry after successful download.

---

### GET `/api/convert/metrics`

Get aggregated task statistics.

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

### POST `/api/ai/summarize`

Summarize a PDF using OpenAI (optional, requires API key).

**Parameters:**
- `file` (form): PDF file
- `length` (form): `short|medium|long`

**Response (HTTP 202):**
```json
{
  "taskId": "string",
  "status": "PENDING"
}
```

---

### GET `/api/ai/status`

Check AI summarization status.

**Response (HTTP 200):**
```json
{
  "message": "string",
  "summary": "string or null"
}
```

## Known Limitations

### Memory-Based Task Storage

- All task state and results stored in JVM heap
- Tasks lost on application restart
- Each task occupies heap space equal to result size + metadata
- Single-instance only; no distributed task tracking

**Impact:** Not suitable for horizontal scaling or high-availability setups without external state store (Redis, database).

### LibreOffice Resource Requirements

- Each office→PDF conversion spawns a LibreOffice process (~500MB)
- Processes may not always terminate cleanly
- Default limit: 8 concurrent conversions (queue overflows after)
- Large spreadsheets or presentations consume significant disk I/O

**Impact:** Expect 4-8 office conversions per instance; pure PDF operations have no such limit.

### PDF Conversions Lose Fidelity

- **PDF → Word:** Text extraction only; layout, fonts, images not preserved
- **PDF → Excel:** Heuristic parsing of whitespace; tables may not extract correctly
- **PDF → PowerPoint:** Text-only conversion; no visual reconstruction

**Impact:** Use for archival or content extraction only, not for format preservation.

### File Size Constraints

- Max upload per file: 100MB (configurable)
- Max request size: 500MB (for batch operations)
- All results held in memory; large PDFs consume heap
- PDF-to-images conversion rasterizes at 150 DPI (high-res PDFs produce large ZIP files)

**Impact:** Monitor heap usage; adjust `-Xmx` if handling large files.

### Security Considerations

- **User-Uploaded Files:** No sandboxing or virus scanning
- **Temporary Files:** Stored in system temp directory; may be readable by other processes on shared systems
- **API Keys:** Stored in plaintext in `application.properties`; use environment variables in production
- **CORS:** Configured for localhost only; adjust for frontend domains in production
- **Rate Limiting:** IP-based; ineffective behind CDNs or proxies without `X-Forwarded-For` header

### Rate Limiting

- 15 requests/hour per IP address (hardcoded)
- Only affects API endpoints
- Not enforced on web UI static assets

### Batch Operations

- Folder structure in ZIP output depends on conversion type
- Errors in one file don't abort batch; errors logged as `.txt` files in ZIP

## Performance Characteristics

All measurements on 2GHz CPU, 8GB RAM, 100MB test files.

| Operation | Time | Notes |
|-----------|------|-------|
| DOCX → PDF | 2-5 sec | LibreOffice subprocess startup overhead |
| PDF → Images (10 pages) | 3-8 sec | Rasterization at 150 DPI |
| Merge 5 PDFs | 1-2 sec | PDFBox operations are fast |
| Compress PDF (10MB) | 2-4 sec | Image re-encoding |
| Images → PDF (10 images) | 1-3 sec | Pure Java; fast |

**Scaling Profile:**
- Single-threaded conversions; 4-8 concurrent recommended
- Memory grows linearly with task result size
- Task cleanup runs hourly; completed tasks expire after 2 hours

## Deployment

### Docker Compose (Development)

```bash
docker-compose up -d
docker-compose logs -f pdf-converter
docker-compose down
```

Volume: `./uploads` (mounted to `/app/uploads` in container; unused in current implementation)

### Docker Manual Build

```bash
docker build -t pdf-converter:latest .
docker run -d \
  -p 8080:8080 \
  -e JAVA_OPTS="-Xmx1024m -Xms512m" \
  -e OPENAI_API_KEY=sk-your-key \
  pdf-converter:latest
```

### Production Deployment (Single Instance)

1. **Build JAR:**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Run with systemd:**
   ```ini
   [Unit]
   Description=PDF Converter
   After=network.target

   [Service]
   Type=simple
   User=pdf-converter
   WorkingDirectory=/opt/pdf-converter
   ExecStart=/usr/bin/java -Xmx2g -Xms1g -jar pdf-converter.jar
   Restart=always
   StandardOutput=journal
   StandardError=journal

   [Install]
   WantedBy=multi-user.target
   ```

3. **Configure:**
   - Set environment variables: `openai.api.key`, `app.cors.allowed-origin`
   - Increase heap if handling large files: `-Xmx4g`

4. **Front with reverse proxy (nginx):**
   ```nginx
   upstream pdf-converter {
       server localhost:8080;
   }

   server {
       listen 443 ssl http2;
       server_name converter.example.com;

       ssl_certificate /etc/letsencrypt/live/converter.example.com/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/converter.example.com/privkey.pem;

       location / {
           proxy_pass http://pdf-converter;
           proxy_set_header X-Forwarded-For $remote_addr;
           proxy_set_header X-Forwarded-Proto $scheme;
           client_max_body_size 100m;
       }
   }
   ```

**Note:** If behind reverse proxy, set `app.rate-limit.trust-forwarded-headers=true` and configure `app.rate-limit.trusted-proxies`.

### Scaling Considerations

This application **does not scale horizontally** without external changes:

- **Shared State:** Requires Redis or database for task registry
- **File Sharing:** Temporary files must be on shared filesystem (NFS, S3)
- **LibreOffice Pooling:** Consider containerized LibreOffice services

For high-availability production use, either:
1. Deploy multiple single-instance services with load balancing (lose task state on instance failure)
2. Externalize task storage to Redis/database
3. Use job queue (RabbitMQ, Kafka) for async processing

## Development

### Local Build

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

### Testing

```bash
mvn test
```

### Debugging

```bash
export JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
mvn spring-boot:run
```

Connect debugger to `localhost:5005`.

### Adding a New Conversion Tool

1. Add tool definition to `ConversionService.TOOLS` static map
2. Implement conversion method in `ConversionService`
3. Add route in `convertSingleFile()` switch statement
4. Update this README

Example:
```java
static {
    toolsMap.put("my-conversion", 
        new ToolDefinition("my-conversion", "pdf", "application/pdf", 
            Set.of("doc", "docx")));
}

private ConversionResult myConversion(MultipartFile file, ToolDefinition toolDef) 
    throws Exception {
    // Implementation
}
```

## Troubleshooting

### LibreOffice Not Found

**Error:** `UnsupportedOperationException: LibreOffice is not available`

**Solution:**
- Ensure LibreOffice is installed: `which soffice`
- Or use Docker (includes LibreOffice)
- Or skip office conversions and use PDF-only operations

### Port 8080 Already in Use

```bash
lsof -i :8080
kill -9 <PID>

# Or change port in application.properties:
server.port=8081
```

### Out of Memory

**Error:** `java.lang.OutOfMemoryError: Java heap space`

**Solutions:**
1. Increase heap: `-Xmx2g` (or higher in docker-compose.yml)
2. Reduce max file size: `spring.servlet.multipart.max-file-size=50MB`
3. Lower task retention: `app.tasks.completed-retention-hours=1`
4. Reduce concurrent conversions: `app.async.max-pool-size=4`

### Rate Limit Blocking Legitimate Traffic

If behind a proxy and `X-Forwarded-For` is not trusted:

```properties
# application.properties
app.rate-limit.trust-forwarded-headers=true
app.rate-limit.trusted-proxies=10.0.0.0/8,172.16.0.0/12
```

### Tasks Not Cleaning Up

Check logs for exceptions in cleanup task:
```bash
docker-compose logs pdf-converter | grep -i cleanup
```

Increase cleanup interval if too aggressive:
```properties
app.tasks.cleanup-interval-ms=7200000  # 2 hours
```

## Future Improvements

- [ ] External task storage (Redis, PostgreSQL) for scalability
- [ ] Job queue (RabbitMQ, Kafka) for reliability
- [ ] WebSocket for real-time progress updates
- [ ] Webhook notifications on task completion
- [ ] S3/cloud storage backend for results
- [ ] OCR support (Tesseract integration)
- [ ] Improved PDF-to-Office fidelity (pypdf, LibreOffice headless)
- [ ] Batch operations optimization (parallel file processing)
- [ ] Resource monitoring and auto-scaling
- [ ] API authentication (OAuth2, API keys)
- [ ] Advanced PDF operations (form filling, signature verification)
- [ ] Image quality configuration options

## License

This project is provided as-is for internal and educational use.

## Support

Refer to logs for detailed error messages:
```bash
# Local
mvn spring-boot:run 2>&1 | grep -i error

# Docker
docker-compose logs pdf-converter --tail=100
```

Check `/api/convert/metrics` to monitor task queue health.

For issues:
1. Review logs and error messages
2. Check application.properties configuration
3. Verify file formats and sizes are within limits
4. Ensure LibreOffice is available for office conversions

