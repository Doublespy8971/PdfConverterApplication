# Async conversion load test

This k6 script submits one DOCX to `word-to-pdf` and one PNG to `images-to-pdf` per
iteration, polls each task until it completes, and downloads each result.

Install k6, provide two small local fixtures, then run against a local application:

```bash
mkdir -p loadtest/fixtures
# Put a valid small.docx and small.png in loadtest/fixtures/
k6 run --vus 1 --duration 1m loadtest/async-conversion.js
```

For another target or fixture location:

```bash
BASE_URL=https://example.test \
DOCX_FILE=/path/to/sample.docx \
IMAGE_FILE=/path/to/sample.png \
VUS=4 DURATION=5m \
k6 run loadtest/async-conversion.js
```

Record the k6 `conversion_duration_ms` p50, p95, and p99, total iterations,
`conversion_failures`, and `conversion_rate_limited`. Also record the test duration,
VUs, fixture sizes, server/JVM/LibreOffice versions, and whether any 429 responses
were observed (including the `Retry-After` behavior).
