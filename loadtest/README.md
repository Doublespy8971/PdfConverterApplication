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

k6 only reports tagged sub-metrics (the per-tool lines) when a threshold references them, so
the options block declares non-gating `max>=0` thresholds.

Run the three measured cases with the load-test rate-limit override:

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d
VUS=1 DURATION=2m k6 run --summary-trend-stats="avg,med,p(90),p(95),p(99),max" loadtest/async-conversion.js
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml restart pdf-converter
VUS=2 DURATION=2m k6 run --summary-trend-stats="avg,med,p(90),p(95),p(99),max" loadtest/async-conversion.js
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml restart pdf-converter
VUS=8 DURATION=2m k6 run --summary-trend-stats="avg,med,p(90),p(95),p(99),max" loadtest/async-conversion.js
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
