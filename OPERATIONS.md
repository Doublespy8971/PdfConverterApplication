# Deployment & Operations

## Pre-Deployment Checklist

- [ ] All tests passing: `mvn test`
- [ ] JAR builds successfully: `mvn clean package`
- [ ] Docker image builds: `docker build -t pdf-converter:latest .`
- [ ] Configuration reviewed (`application.properties`)
- [ ] API keys secured (environment variables only)
- [ ] CORS origin set correctly
- [ ] LibreOffice installed (if office conversions needed)
- [ ] Reverse proxy configured (TLS, rate limiting)
- [ ] Monitoring/logging configured
- [ ] Backup plan documented

## Deployment Options

### Option 1: Standalone JAR (Simplest)

```bash
# Build
mvn clean package -DskipTests

# Run
java -Xmx2g -Xms1g \
  -DOPENAI_API_KEY=sk-... \
  -DAPP_CORS_ALLOWED_ORIGIN=https://yourdomain.com \
  -jar target/pdf-converter-0.0.1-SNAPSHOT.jar
```

**Pros:** Simple, minimal dependencies
**Cons:** Single instance, manual restart on failure

### Option 2: Docker Compose (Recommended for Small Teams)

```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# Logs
docker-compose logs -f pdf-converter
```

**Pros:** Isolated, reproducible, easy scaling
**Cons:** Single container on one host

### Option 3: Kubernetes (Enterprise)

Create `k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pdf-converter
  namespace: default
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pdf-converter
  template:
    metadata:
      labels:
        app: pdf-converter
    spec:
      containers:
      - name: pdf-converter
        image: myregistry/pdf-converter:v1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
        env:
        - name: JAVA_OPTS
          value: "-Xmx1g -Xms512m"
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: pdf-converter-secrets
              key: openai-key
        - name: APP_CORS_ALLOWED_ORIGIN
          value: "https://yourdomain.com"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/convert/metrics
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: pdf-converter-service
spec:
  selector:
    app: pdf-converter
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

Deploy:
```bash
kubectl apply -f k8s/deployment.yaml
```

**Pros:** Highly scalable, resilient, self-healing
**Cons:** Complex setup, infrastructure required

## Configuration Management

### Environment Variables (Recommended)

```bash
# Set before running JAR
export JAVA_OPTS="-Xmx2g -Xms1g"
export OPENAI_API_KEY=sk-...
export APP_CORS_ALLOWED_ORIGIN=https://yourdomain.com
export APP_ASYNC_CORE_POOL_SIZE=8
export APP_ASYNC_MAX_POOL_SIZE=16

java -jar pdf-converter.jar
```

### Systemd Service (Linux)

Create `/etc/systemd/system/pdf-converter.service`:

```ini
[Unit]
Description=PDF Converter Application
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=pdf-converter
Group=pdf-converter
WorkingDirectory=/opt/pdf-converter
ExecStart=/usr/bin/java -Xmx2g -Xms1g -jar pdf-converter.jar
ExecReload=/bin/kill -HUP $MAINPID
Restart=on-failure
RestartSec=10

# Environment variables
EnvironmentFile=/opt/pdf-converter/pdf-converter.env

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/opt/pdf-converter /var/log/pdf-converter

# Limits
LimitNOFILE=65535
LimitNPROC=512

[Install]
WantedBy=multi-user.target
```

Create `/opt/pdf-converter/pdf-converter.env`:

```ini
JAVA_OPTS=-Xmx2g -Xms1g
OPENAI_API_KEY=sk-...
APP_CORS_ALLOWED_ORIGIN=https://yourdomain.com
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable pdf-converter
sudo systemctl start pdf-converter
sudo systemctl status pdf-converter
```

### Reverse Proxy (nginx)

```nginx
# /etc/nginx/sites-available/pdf-converter
upstream pdf_converter {
    server localhost:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name converter.example.com;
    
    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name converter.example.com;
    
    # SSL certificates (e.g., Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/converter.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/converter.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    
    # Rate limiting at proxy level (stronger than app-level)
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
    limit_req zone=api_limit burst=20 nodelay;
    
    # Proxy configuration
    location / {
        proxy_pass http://pdf_converter;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts for long conversions
        proxy_connect_timeout 10s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # Keep-alive
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        
        # Buffering
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
    }
    
    # Increase file upload limit
    client_max_body_size 100M;
    
    # Gzip compression
    gzip on;
    gzip_types application/json text/html text/css;
}
```

Enable:
```bash
sudo ln -s /etc/nginx/sites-available/pdf-converter /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Application Configuration

`application.properties` (or environment variables):

```properties
# Server
server.port=8080

# Multipart uploads
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=500MB

# CORS (set to your frontend domain)
app.cors.allowed-origin=https://yourdomain.com

# AI
ai.provider=openai
openai.api.key=${OPENAI_API_KEY}
openai.model=gpt-3.5-turbo

# Rate limiting
app.rate-limit.trust-forwarded-headers=true
app.rate-limit.trusted-proxies=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16

# Async processing
app.async.core-pool-size=4
app.async.max-pool-size=8
app.async.queue-capacity=100

# Task management
app.tasks.completed-retention-hours=2
app.tasks.processing-timeout-hours=6
app.tasks.cleanup-interval-ms=3600000
```

## Performance Tuning

### JVM Heap Sizing

| File Size | Concurrent | Heap |
|-----------|-----------|------|
| < 10MB | 8 | 512MB |
| 10-50MB | 8 | 1GB |
| 50-100MB | 4 | 2GB |
| > 100MB | 2 | 4GB+ |

```bash
# Minimum/maximum heap
-Xms1g  # Initial heap
-Xmx2g  # Maximum heap

# Garbage collection
-XX:+UseG1GC            # Use G1GC for large heaps
-XX:MaxGCPauseMillis=200  # Target pause time
```

### Thread Pool Sizing

```properties
# Per CPU core
app.async.core-pool-size=4      # = number of CPU cores
app.async.max-pool-size=8       # = 2x CPU cores
app.async.queue-capacity=100    # 5-10x max pool size
```

**Example (8-core system):**
- Core: 8 threads
- Max: 16 threads
- Queue: 100 tasks

### Connection Pooling

Nginx upstream configuration:
```nginx
upstream pdf_converter {
    server localhost:8080 max_fails=3 fail_timeout=30s;
    keepalive 32;  # Connection pool size
}
```

### Caching

For static assets (JavaScript, CSS):
```nginx
location ~* \.(js|css|png|jpg|jpeg|gif|ico|woff|woff2)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

## Monitoring & Observability

### Logging Aggregation

**Docker Compose:**
```yaml
services:
  pdf-converter:
    # ...
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "10"
```

**Forwarding logs to ELK/CloudWatch:**

```bash
# Using logstash
# /etc/logstash/conf.d/pdf-converter.conf
input {
  file {
    path => "/var/log/pdf-converter/app.log"
    start_position => "beginning"
  }
}

filter {
  grok {
    match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{DATA:class} - %{GREEDYDATA:msg}" }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
  }
}
```

### Metrics

**Prometheus scraping:**

```bash
# Add dependency to pom.xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Prometheus config:
```yaml
scrape_configs:
  - job_name: 'pdf-converter'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

### Health Checks

```bash
# Liveness (is app running?)
curl http://localhost:8080/health

# Readiness (can app accept requests?)
curl http://localhost:8080/api/convert/metrics

# Task queue health
curl http://localhost:8080/api/convert/metrics | jq '.processingTasks'
```

Kubernetes probes:
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  
readinessProbe:
  httpGet:
    path: /api/convert/metrics
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

### Alerting

**Example alerts (Prometheus):**

```yaml
groups:
  - name: pdf-converter
    interval: 30s
    rules:
    
    - alert: HighErrorRate
      expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
      for: 5m
      annotations:
        summary: "High error rate detected"
    
    - alert: QueueBacklog
      expr: task_queue_length > 50
      for: 10m
      annotations:
        summary: "Too many queued tasks"
    
    - alert: HighMemoryUsage
      expr: jvm_memory_usage_percent > 90
      for: 5m
      annotations:
        summary: "JVM heap nearly full"
```

## Backup & Recovery

### Data Backup

Task state is in-memory; no persistent data to backup. But:

1. **Application state:** None to backup (ephemeral)
2. **Configuration:** Backup `application.properties` or env file
3. **Logs:** Archive for compliance (optional)

### Disaster Recovery

**Single-instance failure:**

1. Restart container/JAR
2. In-progress tasks are lost
3. Completed tasks are lost
4. Clients must resubmit

**RTO:** < 1 minute (container restart)
**RPO:** 0 (no state persisted)

**High-availability setup:**

1. Deploy multiple instances
2. Load balancer distributes traffic
3. Failed instance replaced automatically
4. Use external state store (Redis, database)

### Restore from Backup

Since no persistent data:

```bash
# Restart from image/JAR
docker-compose up -d

# Or
systemctl restart pdf-converter
```

## Operational Tasks

### View Logs

```bash
# Docker
docker-compose logs -f pdf-converter
docker-compose logs --tail=100 pdf-converter

# Systemd
journalctl -u pdf-converter -f
journalctl -u pdf-converter --tail=100

# File
tail -f /var/log/pdf-converter/app.log
```

### Check Status

```bash
# Health
curl http://localhost:8080/health

# Metrics
curl http://localhost:8080/api/convert/metrics | jq

# Running tasks
curl http://localhost:8080/api/convert/metrics | jq '.processingTasks'
```

### Scale Horizontally

**Docker Compose:**
```bash
docker-compose up -d --scale pdf-converter=3
```

**Kubernetes:**
```bash
kubectl scale deployment pdf-converter --replicas=5
```

**Limitations:** In-memory task storage not shared; each instance independent.

### Update Application

```bash
# Docker
docker-compose down
docker-compose pull
docker-compose up -d

# Systemd
systemctl stop pdf-converter
# Replace JAR at /opt/pdf-converter/pdf-converter.jar
systemctl start pdf-converter
```

**Zero-downtime with load balancer:**

1. Start new instance on different port
2. Add to load balancer
3. Remove old instance
4. Repeat for rolling restart

### Troubleshoot Production Issues

**Issue: High memory usage**
```bash
# Check heap
jcmd <PID> GC.class_histogram | head -20

# Check tasks in memory
curl http://localhost:8080/api/convert/metrics

# Solution: Reduce retention hours
# app.tasks.completed-retention-hours=1
# Then restart
```

**Issue: Slow conversions**
```bash
# Check task queue
curl http://localhost:8080/api/convert/metrics | jq '.processingTasks'

# If high: Increase threads
# app.async.max-pool-size=16
# Then restart
```

**Issue: Conversions failing**
```bash
# Check logs for error
docker-compose logs pdf-converter | grep ERROR

# Verify LibreOffice
soffice --version

# Test conversion manually
curl -X POST -F "file=@test.docx" http://localhost:8080/api/convert/word-to-pdf
```

## Compliance & Auditing

### HIPAA/PCI-DSS Considerations

Not implemented; requires:

1. **Authentication:** Add OAuth2 layer (proxy)
2. **Encryption:** TLS for transit, at-rest encryption for storage
3. **Audit Logging:** All access logged with user/timestamp
4. **Data Retention:** Automatic deletion policies
5. **Access Control:** Role-based permissions

### GDPR Considerations

- Users can request data deletion (no persistent data)
- PII handling (upload encrypted files, no storage)
- Data processing agreements (use GDPR-compliant AI provider)

## License Compliance

- **Spring Boot:** Apache 2.0
- **PDFBox:** Apache 2.0
- **Apache POI:** Apache 2.0
- **OkHttp:** Apache 2.0
- **Bucket4j:** Apache 2.0
- **Caffeine:** Apache 2.0

All compatible; no license conflicts.

## Disaster Scenarios

### Scenario 1: LibreOffice Crashes

```
Symptom: office-to-pdf conversions fail
Fix:
  1. Check: soffice --version
  2. Restart: pkill soffice
  3. Verify: soffice --version
  4. Retry conversion
```

### Scenario 2: Disk Space Exhausted

```
Symptom: All conversions fail; logs show "No space left"
Fix:
  1. Check: df -h /tmp
  2. Clean: rm -rf /tmp/convert_* /tmp/merge_*
  3. Monitor: app.tasks.cleanup-interval-ms
  4. Consider: Increase disk or reduce temp file retention
```

### Scenario 3: Memory Leak

```
Symptom: Memory increases over time; GC becomes frequent
Fix:
  1. Verify: jcmd <PID> GC.class_histogram
  2. Check: Tasks retained too long
  3. Restart: Clean restart (tasks lost)
  4. Monitor: Watch heap over time
```

### Scenario 4: External API Failure (OpenAI)

```
Symptom: AI summarization fails; HTTP 500 errors
Fix:
  1. Check: curl https://status.openai.com/
  2. Verify: OPENAI_API_KEY environment variable
  3. Retry: Client-side retry logic
  4. Fallback: Disable AI feature if critical
```

## Capacity Planning

**For 100 concurrent users:**

- CPU: 4 cores (2-4 conversions in parallel)
- RAM: 4GB (2GB heap + OS)
- Storage: 50GB (/tmp for temp files)
- Network: 10 Mbps (file uploads)

**For 1000 concurrent users:**

- CPU: 16+ cores (multiple instances)
- RAM: 16GB+ (load balancer + 3+ app instances)
- Storage: 500GB+ (queue, temp files)
- Network: 100 Mbps+

**Better approach:** Start small (1 instance) and scale based on metrics:
- If CPU > 70%: Add instance or increase cores
- If memory > 80%: Reduce retention or increase heap
- If queue > 50: Increase workers or heap

## Cost Estimation (AWS Example)

**Small deployment (50 req/day):**
- EC2 t3.small: $0.023/hour = $170/month
- Data transfer: < $1/month
- Total: ~$170/month

**Medium deployment (5000 req/day):**
- EC2 t3.large: $0.092/hour = $680/month
- RDS (task state): $50-100/month
- Data transfer: $10-50/month
- Total: ~$750/month

**Large deployment (100k req/day):**
- EKS cluster: $0.10/hour + compute = $1000+/month
- RDS Multi-AZ: $300+/month
- Data transfer: $500+/month
- OpenAI API: $1000+/month (heavily depends on usage)
- Total: $2000+/month

## Support & Escalation

### Issue Resolution Path

1. **Check logs:** `docker-compose logs pdf-converter | grep ERROR`
2. **Check metrics:** `curl http://localhost:8080/api/convert/metrics`
3. **Test endpoint:** `curl -X POST -F "file=@test.docx" http://localhost:8080/api/convert/word-to-pdf`
4. **Restart:** `docker-compose down && docker-compose up -d`
5. **Escalate:** Review architecture/deployment docs, contact vendor

### Documentation

- [README_PROFESSIONAL.md](README_PROFESSIONAL.md) - Setup & usage
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical deep-dive
- [SECURITY.md](SECURITY.md) - Security model
- [DEVELOPMENT.md](DEVELOPMENT.md) - Development setup
- [This file](OPERATIONS.md) - Deployment & operations

## Change Management

1. **Plan:** Review changes, impact, rollback plan
2. **Test:** Verify on staging, load test if applicable
3. **Deploy:** Off-peak hours, gradual rollout if possible
4. **Monitor:** Watch metrics for 30 minutes post-deploy
5. **Rollback:** If issues, revert to previous version

Example deployment script:

```bash
#!/bin/bash
set -e

VERSION=${1:-v1.0.0}
BACKUP_JAR="/opt/pdf-converter/backup-$(date +%s).jar"

echo "Deploying $VERSION..."

# Backup current
cp /opt/pdf-converter/pdf-converter.jar $BACKUP_JAR

# Download new version
wget -O /opt/pdf-converter/pdf-converter.jar \
  https://releases.example.com/pdf-converter/$VERSION/pdf-converter.jar

# Restart
systemctl restart pdf-converter

# Verify
sleep 5
curl http://localhost:8080/health || {
  echo "Health check failed; rolling back..."
  cp $BACKUP_JAR /opt/pdf-converter/pdf-converter.jar
  systemctl restart pdf-converter
  exit 1
}

echo "Deployment successful!"
rm $BACKUP_JAR
```

