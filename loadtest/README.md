# Async conversion load test

This k6 script submits one DOCX to `word-to-pdf` and one PNG to `images-to-pdf` per
iteration, polls each task until it completes, and downloads each result.

Install k6 and generate the two small local fixtures:

```bash
python3 loadtest/fixtures/generate-fixtures.py
```

The fixture paths are relative to this script's folder, `loadtest/`, not the working
directory. The target must raise `app.rate-limit.requests-per-hour` for the test duration;
otherwise the default 15 requests per hour can produce 429 responses while submitting
conversions. Status polling and downloads are excluded from the rate-limited paths.

The options block lists thresholds for both tagged tool series:
`conversion_duration_ms{tool:word-to-pdf}` and
`conversion_duration_ms{tool:images-to-pdf}`. The `max>=0` thresholds are intentionally
non-gating; they make the per-tool series explicit without asserting a performance target.

Run the three measured cases with the load-test rate-limit override:

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d
k6 run --vus 1 --duration 2m loadtest/async-conversion.js
docker compose restart pdf-converter
k6 run --vus 2 --duration 2m loadtest/async-conversion.js
docker compose restart pdf-converter
k6 run --vus 8 --duration 2m loadtest/async-conversion.js
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
