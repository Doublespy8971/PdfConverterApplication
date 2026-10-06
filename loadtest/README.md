# Async conversion load test

This k6 script submits one DOCX to `word-to-pdf` and one PNG to `images-to-pdf` per
iteration, polls each task until it completes, and downloads each result.

Install k6 and generate the two small local fixtures:

```bash
python3 loadtest/fixtures/generate-fixtures.py
```

The target must raise `app.rate-limit.requests-per-hour` for the test duration;
otherwise the default 15 requests per hour will produce 429 responses while
submitting, polling, and downloading conversions. Then run against a local application:

```bash
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
