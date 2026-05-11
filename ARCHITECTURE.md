# Architecture & Design

## System Architecture

```
Client Layer
├─ Web UI (HTML/JS)
│  └─ REST API client
└─ HTTP clients (cURL, Postman, etc.)

API Layer (Spring Boot)
├─ ConverterController
│  ├─ POST /api/convert/{tool} → initiate async conversion
│  ├─ GET /api/convert/status/{taskId} → poll status
│  └─ GET /api/convert/download/{taskId} → fetch result
│
├─ AIController (optional)
│  └─ POST /api/ai/summarize → AI summarization
│
└─ RateLimitingInterceptor
   └─ 15 req/hour per IP

Service Layer
├─ TaskRegistryService
│  └─ In-memory ConcurrentHashMap<taskId, TaskStatus>
│     └─ Stores: status, result byte[], filename, content-type, error
│
├─ AsyncConversionWorker
│  └─ ThreadPoolExecutor (4-8 threads)
│     └─ Processes: convertFileAsync, mergePdfsAsync, batchConvertAsync
│
├─ ConversionService
│  ├─ PDFBox operations
│  │  ├─ convertPdfToImages
│  │  ├─ convertImagesToPdf
│  │  ├─ splitPdf
│  │  ├─ mergePdf
│  │  └─ compressPdf
│  │
│  └─ Office operations (delegate to LibreOffice)
│     ├─ word-to-pdf
│     ├─ excel-to-pdf
│     └─ powerpoint-to-pdf
│
├─ LibreOfficeConverterService
│  └─ Spawns subprocess: soffice --headless --convert-to pdf
│
└─ LLMProvider (conditional)
   ├─ OpenAIProvider (uses OkHttp to call OpenAI API)
   └─ GeminiProvider (uses OkHttp to call Google API)

Persistence Layer
├─ Temporary Files: $JAVA_TMPDIR/convert_<taskId>/
├─ Task State: JVM heap (ConcurrentHashMap)
├─ Results: JVM heap (byte arrays, up to 100MB each)
└─ Cleanup: Scheduled task every 1 hour
```

## Request Lifecycle

### Conversion Flow

```
1. POST /api/convert/word-to-pdf with file
   │
   ├─ ConverterController.convertFile()
   │  ├─ Validate: file not empty
   │  ├─ Generate taskId (UUID)
   │  ├─ Create temp directory: $TEMP/convert_<taskId>/
   │  ├─ Save uploaded file (streaming, not in memory)
   │  ├─ Initiate task in TaskRegistryService
   │  ├─ Submit async job to AsyncConversionWorker
   │  └─ Return HTTP 202 Accepted with taskId
   │
   ├─ [AsyncConversionWorker thread pool processes:]
   │  │
   │  ├─ updateTaskProgress(taskId) → Status: PENDING → PROCESSING
   │  │
   │  ├─ ConversionService.convertSingleFile()
   │  │  ├─ Route to word-to-pdf handler
   │  │  ├─ Call LibreOfficeConverterService.convertOfficeDocumentToPdf()
   │  │  │  ├─ Create temp file: /tmp/temp<random>.pdf
   │  │  │  ├─ Execute: soffice --headless --convert-to pdf <input>
   │  │  │  ├─ Wait for subprocess completion
   │  │  │  ├─ Read PDF bytes from temp file
   │  │  │  └─ Delete temp file
   │  │  │
   │  │  └─ Return ConversionResult(bytes, filename, contentType)
   │  │
   │  ├─ completeTask(taskId, resultBytes, "document.pdf", "application/pdf")
   │  │  └─ Status: PROCESSING → COMPLETED
   │  │  └─ Store result in heap
   │  │
   │  └─ [Exception handling:]
   │     └─ failTask(taskId, errorMessage)
   │        └─ Status: PROCESSING → FAILED
   │
   └─ [Client polls status:]
      │
      ├─ GET /api/convert/status/<taskId>
      │  └─ Return current status from TaskRegistryService
      │
      └─ [Once COMPLETED:]
         │
         └─ GET /api/convert/download/<taskId>
            ├─ Retrieve result bytes from TaskRegistryService
            ├─ Return file with Content-Disposition: attachment
            ├─ Remove task from registry (cleanup)
            └─ HTTP 200 OK
```

## Data Models

### TaskStatus (In-Memory Registry)

```java
class TaskStatus {
    String status;              // PENDING, PROCESSING, COMPLETED, FAILED
    byte[] resultContent;       // Conversion result (100MB max)
    String fileName;            // Output filename
    String contentType;         // MIME type (e.g., application/pdf)
    String errorMessage;        // If FAILED
    long createdAt;            // Milliseconds since epoch
    long updatedAt;            // Last status change
}
```

**Lifetime:**
- Created: Task initiated (HTTP POST)
- Updated: Status changes (PENDING → PROCESSING → COMPLETED/FAILED)
- Expired: After TTL (2 hours for COMPLETED, 6 hours for PENDING/PROCESSING)
- Deleted: After download OR after TTL

### ConversionResult (Internal)

```java
record ConversionResult(
    byte[] content,            // Converted file bytes
    String fileName,           // Output filename
    String contentType         // MIME type
)
```

### ToolDefinition (Immutable)

```java
record ToolDefinition(
    String key,                // e.g., "word-to-pdf"
    String outputExtension,    // e.g., "pdf"
    String contentType,        // e.g., "application/pdf"
    Set<String> allowedExtensions  // Input formats allowed
)
```

## Concurrency Model

### Thread Pools

1. **HTTP Request Thread Pool** (Spring embedded Tomcat)
   - Default: 200 threads
   - Handles incoming requests; delegates conversion to worker pool

2. **Async Conversion Worker Pool** (Custom ThreadPoolExecutor)
   - Core threads: 4 (configurable via `app.async.core-pool-size`)
   - Max threads: 8 (configurable via `app.async.max-pool-size`)
   - Queue capacity: 100 (configurable via `app.async.queue-capacity`)
   - Rejection policy: Caller runs (HTTP request thread blocks if queue full)

### Thread Safety

- **TaskRegistryService:** Uses `ConcurrentHashMap` for thread-safe task storage
- **TaskStatus:** No synchronization within object; races on field updates unlikely (single writer per task)
- **ConversionService:** Stateless; safe for concurrent calls
- **LibreOfficeConverterService:** Each conversion spawns separate process; no shared state

### Race Conditions

1. **Task Download Before Completion:**
   - Status polled; if still PROCESSING, GET download returns 202 Accepted

2. **Concurrent Download Attempts:**
   - First download retrieves result and removes task
   - Second download gets 404 (task not found)
   - Safe; no data corruption

3. **Task Status Update During Cleanup:**
   - Cleanup expiration check reads `createdAt` timestamp
   - Status updated concurrently by conversion worker
   - `updatedAt` may change; `createdAt` immutable
   - Safe; worst case: task not cleaned up for another hour

## Memory Management

### Heap Usage Breakdown

```
Baseline:                     ~100 MB
Per active task (status):     ~1 KB
Per result (byte array):      = file size

Example (8 concurrent conversions):
├─ Baseline:                  100 MB
├─ Task metadata:             ~10 KB
├─ 8 results @ 50MB each:     400 MB
└─ JVM overhead:              ~150 MB
└─ Total:                     ~650 MB
```

**Default Heap:** 512 MB (`-Xms256m -Xmx512m`)
- Safe for small files (< 50MB)
- Insufficient for 8 concurrent 100MB files

**Recommended for Production:**
- Small files (< 25MB): `-Xmx1g`
- Medium files (< 100MB): `-Xmx2g`
- Large files (> 100MB): `-Xmx4g` or more

### Garbage Collection

- Default: G1GC (good for large heaps)
- Pauses: Tuned for responsiveness, not latency-sensitive
- Full GC triggered: Only if heap exhausted
- Monitoring: Use `jcmd <pid> GC.class_histogram` to check memory by type

### Memory Leaks (Known Issues)

None identified, but:

1. **LibreOffice Subprocess:** May not fully release memory if killed
2. **Exception in Conversion:** Resources may not clean up (streams left open)
3. **Task Cleanup Failure:** Tasks remain in memory if cleanup disabled

## File Handling

### Upload Flow

```
MultipartFile (Spring)
    ↓
[Validate: not empty, valid extension]
    ↓
[Streaming transfer to disk]
    ↓
$JAVA_TMPDIR/convert_<taskId>/<filename>
    ↓
[Pass path string to conversion logic]
    ↓
[Read file for processing]
    ↓
[Delete temp file on completion/error]
```

**Key:** Files streamed to disk immediately, not buffered in memory.

### Temporary Directory

```
Platform          | Default Location
Windows           | C:\Users\<user>\AppData\Local\Temp
Linux/macOS       | /tmp
Java override     | System property: java.io.tmpdir
```

**Permissions:** Inherited from system temp (usually world-readable on shared systems).

### Cleanup

```
Success path:
  → Result stored in heap
  → Temp file deleted
  → Task kept for 2 hours
  → Downloaded and removed

Failure path:
  → Error logged
  → Temp files deleted
  → Task kept for 2 hours (status: FAILED)
  → Removed on cleanup interval

Orphaned files (if app crashes):
  → Left in /tmp
  → System cleanup (varies by OS)
  → Manual cleanup possible via `app.tasks.cleanup-interval-ms`
```

## Conversion Strategy

### PDFBox Operations (Pure Java)

- No external dependencies
- Deterministic; same input → same output
- Single-threaded per conversion
- Fast (1-5 seconds typical)

Ops:
- PDF → Images (rasterize at DPI)
- Images → PDF (embed in page)
- Split PDF (extract pages)
- Merge PDF (concatenate pages)
- Compress PDF (reduce image resolution)

### LibreOffice Operations (Subprocess)

- External process spawned per conversion
- Heavy resource usage (~500MB per conversion)
- Slow (2-5 seconds + startup overhead)
- Can hang or crash

Ops:
- Word → PDF
- Excel → PDF
- PowerPoint → PDF

**Subprocess management:**
```java
ProcessBuilder pb = new ProcessBuilder(
    "soffice",
    "--headless",
    "--convert-to", "pdf",
    "--outdir", outputDir,
    inputFile
);
Process process = pb.start();
int exitCode = process.waitFor(); // Blocks until completion
```

**No timeouts; long-running conversions block worker thread.**

### PDF Text Extraction

Used for:
- PDF → Word (extract text, create paragraphs)
- PDF → Excel (extract text, split by whitespace/tabs)
- PDF → PowerPoint (extract text, create slides)

**Limitations:**
- Text-only extraction; layout lost
- Heuristic splitting (whitespace, newlines)
- No table structure preservation

## Rate Limiting

### Token Bucket Algorithm (Bucket4j)

```java
Bucket bucket = Bucket4j.builder()
    .addLimit(Limit.of(15, Refill.intervally(15, Duration.ofHours(1))))
    .build();

boolean allowed = bucket.tryConsume(1);
```

- 15 tokens per 1-hour window per IP
- Tokens refill continuously over hour (15 tokens / 3600 seconds)
- Consumed per request (1 token/request)
- Uses in-memory Caffeine cache (expires if no requests for token bucket)

### Interception

- **Path:** `RateLimitingInterceptor.preHandle()`
- **Invoked:** Before request reaches controller
- **Header:** Reads client IP from request
  - Default: `request.getRemoteAddr()` (source IP)
  - If enabled: `X-Forwarded-For: <first IP>` (leftmost)
- **Trusted Proxies:** Configured whitelist (optional)

### Limits

- Per-IP only (no per-user, per-endpoint, or per-action limits)
- Hardcoded 15 req/hour (configurable in code, not properties)
- No burst allowance; strict even distribution
- Applies to API only (not static assets)

## Async Processing

### ThreadPoolExecutor Configuration

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize: 4,           // Min threads always alive
    maxPoolSize: 8,            // Max threads created
    keepAliveTime: 60,         // Seconds idle before shutdown
    unit: TimeUnit.SECONDS,
    workQueue: new LinkedBlockingQueue<>(100),  // Buffer size
    rejectionPolicy: CallerRunsPolicy  // Caller blocks if queue full
);
```

### Task Queuing

1. **Request arrives:** HTTP thread calls `asyncConversionWorker.convertFileAsync()`
2. **Submitted to queue:** `executor.execute(task)`
3. **Queued (if threads busy):** Task waits in 100-item queue
4. **Queue full:** Caller thread (HTTP handler) blocks until space available
5. **Processing:** Worker thread dequeues and processes

**Implication:** If queue overflows, HTTP request blocks (HTTP client times out, no 503 returned).

### Error Handling

```java
try {
    // Perform conversion
} catch (Exception e) {
    taskRegistryService.failTask(taskId, e.getMessage());
    logger.error("Conversion failed", e);
}
```

- Exceptions logged
- Task marked as FAILED
- No retry logic
- HTTP request not notified (async; client polls for status)

## Dependency Injection

Spring Boot auto-wiring handles:

```java
// Controllers autowire services
@RestController
public class ConverterController {
    @Autowired
    private TaskRegistryService taskRegistryService;
}

// Optional beans (LibreOffice)
@Autowired(required = false)
private LibreOfficeConverterService libreOfficeConverter;
```

**Conditional:** If LibreOffice not available, service not instantiated; conversions fail gracefully.

## Configuration

### Property Sources (Ordered)

1. Command-line args: `--server.port=8081`
2. Environment variables: `SERVER_PORT=8081`
3. `application.properties` file
4. Defaults (hardcoded in code)

### Profiles (Not Implemented)

Application does not use Spring profiles. To use profiles:

```properties
# application-prod.properties
server.port=8080
app.cors.allowed-origin=https://converter.example.com
```

Then: `java -jar app.jar --spring.profiles.active=prod`

## Monitoring & Observability

### Logging

- **Level:** INFO (root), DEBUG (application package)
- **Destination:** Console (Spring default) or file (if configured)
- **Format:** Standard Spring (timestamp, level, class, message)

**Key logs:**
```
Task initiated: <taskId>
Task <taskId> status updated to PROCESSING
Task <taskId> completed successfully: <filename>
Task <taskId> failed: <error>
Downloading completed task: <taskId>
```

### Metrics Endpoint

```bash
curl http://localhost:8080/api/convert/metrics
{
  "totalTasks": 42,
  "pendingTasks": 2,
  "processingTasks": 1,
  "completedTasks": 35,
  "failedTasks": 4
}
```

- Not authenticated
- Real-time counts
- No historical data
- No alerting

### Health Check

```bash
curl http://localhost:8080/health
# Spring Actuator (if enabled)
```

Currently used only in Docker HEALTHCHECK (runs `curl /health` periodically).

## Testing

### Unit Tests

- `TaskRegistryServiceTest`: Task lifecycle, cleanup expiration
- `FileNameUtilsTest`: Filename sanitization

### Manual Testing

```bash
# Single file
curl -X POST -F "file=@test.docx" http://localhost:8080/api/convert/word-to-pdf

# Batch
curl -X POST -F "files=@test1.docx" -F "files=@test2.docx" \
  http://localhost:8080/api/convert/batch/word-to-pdf

# Status
curl http://localhost:8080/api/convert/status/<taskId>

# Download
curl -O http://localhost:8080/api/convert/download/<taskId>
```

### Load Testing

```bash
# Using Apache Bench
ab -n 100 -c 10 http://localhost:8080/

# Using wrk (better)
wrk -t4 -c100 -d30s --script=upload.lua http://localhost:8080/api/convert/word-to-pdf
```

**Limitations:** No built-in benchmarks; manual setup required.

## Future Architecture Changes

1. **Externalize Task State:**
   - Replace TaskRegistryService with Redis backend
   - Enable horizontal scaling

2. **Job Queue:**
   - Replace ThreadPoolExecutor with RabbitMQ/Kafka
   - Enable asynchronous job processing, retry logic

3. **WebSocket:**
   - Replace polling with push updates
   - Real-time progress on `/api/convert/status/{taskId}`

4. **Object Storage:**
   - Replace in-heap results with S3/GCS
   - Store results for longer than 2 hours
   - Enable result download URLs

5. **Metrics & Observability:**
   - Add Prometheus metrics
   - Structured logging (JSON format)
   - Distributed tracing (Jaeger, Zipkin)

6. **Containerization:**
   - Split LibreOffice into separate service
   - Scale conversion workers independently
   - Resource limits per service

