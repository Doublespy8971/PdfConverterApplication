# Security Considerations

## Overview

This document outlines the security model, current protections, and limitations of the PDF Converter Application.

## Authentication & Authorization

**Current Status:** None. The application is intentionally unauthenticated and open to all network clients.

- No API key authentication
- No user login required
- No role-based access control
- Rate limiting is the only access control mechanism

**Implication:** Deploy only on trusted networks or behind an authenticating reverse proxy.

## Rate Limiting

The application implements token bucket rate limiting via Bucket4j:
- **Default:** 15 requests/hour per IP address
- **Algorithm:** Token bucket with 1-hour window
- **Scope:** All `/api/**` endpoints
- **Bypass:** Not possible (hardcoded in interceptor)

### Limitations

- **IP-Based:** Ineffective behind CDNs, reverse proxies, or NAT (all requests appear from same IP)
- **Trust Model:** Must explicitly enable `X-Forwarded-For` header trust:
  ```properties
  app.rate-limit.trust-forwarded-headers=true
  app.rate-limit.trusted-proxies=10.0.0.0/8,172.16.0.0/12
  ```
  If enabled, any proxy can spoof client IP.
- **Tuning:** No per-endpoint or per-user limits; only global per-IP

**Recommendation:** For production, replace with WAF-based rate limiting or API gateway layer.

## File Upload Security

### Validation

- **File Type:** Checked by file extension (case-insensitive) against whitelist per tool
- **File Size:** Enforced by Spring (100MB per file, 500MB per request)
- **Filename Sanitization:** Filenames sanitized to prevent path traversal:
  ```java
  // Removes: .., /, \, null bytes, etc.
  String safe = FileNameUtils.sanitizeFileName(filename);
  ```

### Limitations

- **Magic Byte Validation:** Not performed; extension-based validation only
- **Polyglot Files:** A malicious `.pdf` file containing executable code could be processed
- **Temporary Files:** Stored in system temp directory (`/tmp`, `%TEMP%`), accessible to other processes on shared systems
- **No Antivirus:** No malware scanning; files processed as-is

### Recommendations

1. **Pre-upload scanning:** Integrate ClamAV or similar before processing
2. **Sandboxing:** Run LibreOffice in isolated container or VM
3. **Filesystem permissions:** Ensure temp directory is not world-readable
4. **Quarantine:** Store uploaded files for retention/forensics

## LibreOffice Integration

LibreOffice runs as a subprocess with elevated permissions (same user as application):

**Risks:**
- Malicious documents can exploit LibreOffice CVEs
- Process may hang or crash, consuming resources
- No resource limits (memory, CPU, disk I/O)
- Can access local filesystem

**Mitigations:**
- Keep LibreOffice updated
- Run application as non-root user
- Consider containerized LibreOffice with cgroup limits
- Monitor subprocess crashes

## CORS Configuration

CORS is configured centrally in `SecurityConfig`:

```java
config.setAllowedOrigins(List.of(allowedOrigin));
config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
config.setAllowedHeaders(List.of("*"));
```

**Default:** `http://localhost:8080`

**Current:** All headers accepted; credentials allowed.

**Implications:**
- Any origin can read CORS responses (no origin restriction by default)
- Credentials (cookies) can be sent with cross-origin requests
- Allows preflight OPTIONS caching (1 hour)

**For Production:**
```properties
app.cors.allowed-origin=https://yourdomain.com
```

Update if frontend deployed on different domain.

## API Security

### CSRF Protection

- Enabled for browser UI (`/`)
- Disabled for API endpoints (`/api/**`) to allow cross-origin requests
- Only protects state-changing operations served as HTML forms

**Implication:** API consumers can POST directly; no CSRF token required.

### Headers

The following security headers are set by `SecurityConfig`:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Permissions-Policy: geolocation=(), microphone=(), camera=()
Content-Security-Policy: default-src 'self'; ...
```

These protect browser-based clients; API consumers not affected.

## AI Provider Integration (OpenAI/Gemini)

### API Key Storage

- Keys stored in plaintext in `application.properties` or environment variables
- No encryption at rest

**Exposure Risk:** If production server is compromised, API keys are readable.

**Mitigation:** Use environment variables only:
```bash
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-3.5-turbo
```

Or integrate with secrets management (AWS Secrets Manager, HashiCorp Vault):
```java
// Example (not implemented)
String key = secretsClient.getSecret("openai/api-key");
```

### API Call Security

- Keys sent in HTTP `Authorization: Bearer` header to OpenAI
- If deployed over HTTP (not HTTPS), keys visible to network traffic
- No input sanitization before sending to OpenAI (user PDFs sent as-is for analysis)

**Implication:** Always deploy behind TLS/SSL.

### Rate Limiting

- OpenAI enforces own rate limits (check docs)
- Application has no per-user quota; anyone can burn your API key

**Recommendation:** Monitor usage and set budget alerts in OpenAI console.

## Data Protection

### In-Transit

- No HTTPS enforcement in application (depends on reverse proxy)
- Temporary files sent unencrypted between handlers
- Results sent unencrypted to client

**Production:** Deploy behind TLS-terminating reverse proxy (nginx, AWS ELB, etc.).

### At-Rest

- Task results stored as byte arrays in JVM heap (not encrypted)
- Temporary files on disk unencrypted
- No key material (API keys) encrypted

**Limitation:** JVM heap is readable by other processes running as same user.

### Retention & Cleanup

- Completed tasks: Kept in memory for 2 hours (configurable)
- Processing tasks: Cleared if > 6 hours old (configurable)
- Temporary files: Deleted after conversion
- Downloads: Task removed from registry after download (automatic cleanup)

**Implication:** Memory persists across requests; no automatic per-request cleanup.

## Dependency Security

Dependencies are pinned in `pom.xml` and should be reviewed regularly for CVEs:

Key libraries:
- Spring Boot 3.2.4
- PDFBox 2.0.33
- Apache POI 5.2.5
- OkHttp 4.11.0

**Process:**
```bash
# Check for known vulnerabilities
mvn dependency:check
```

Or use GitHub Dependabot, Snyk, or similar.

## Logging & Monitoring

### Logging

- Application logs at DEBUG level for `com.pm.pdfconverterapplication`
- Logs include filenames, task IDs, and error messages
- No sensitive data filtering (API keys, file contents not logged, but paths/names are)

**Implication:** Logs may contain user data or file information; secure accordingly.

### Monitoring

- `/api/convert/metrics` endpoint exposes task counts (no auth required)
- Can be used to infer system load or availability

**Hardening:** Protect `/metrics` endpoint behind authentication.

## Known Vulnerabilities

### Spring Security

Fixed in current version (3.2.4); no known CVEs. See Spring Security releases.

### PDFBox

PDFBox 2.0.33 should be checked for CVEs. Update if issues found:
```bash
mvn versions:display-dependency-updates
```

### LibreOffice

Update regularly; known vulnerabilities in older versions (especially around OLE2 processing, macros).

```bash
# Check installed version
soffice --version

# Ubuntu
sudo apt-get upgrade libreoffice
```

## Deployment Hardening Checklist

- [ ] TLS/SSL configured on reverse proxy
- [ ] Application runs as non-root user
- [ ] Firewall restricts access to port 8080 (reverse proxy only)
- [ ] Rate limiting tuned or replaced with WAF
- [ ] API keys in environment variables, not code
- [ ] CORS origin set to frontend domain only
- [ ] Logging aggregated and monitored (ELK, CloudWatch, etc.)
- [ ] File upload scanner integrated (ClamAV, Virustotal, etc.)
- [ ] LibreOffice containerized with resource limits
- [ ] Regular security updates applied (Spring, dependencies, OS)
- [ ] Backup/recovery plan tested
- [ ] `/metrics` endpoint protected or disabled
- [ ] Temp directory permissions restricted (mode 700)
- [ ] API documentation (swagger/openapi) disabled in production
- [ ] Consider API authentication layer (OAuth2 proxy, API gateway)

## Incident Response

### If API Key Exposed

1. Rotate immediately in OpenAI console
2. Update environment variables
3. Restart application
4. Review usage logs for unauthorized calls
5. Set stricter budget alerts

### If Malicious Upload Suspected

1. Review temporary file directory for suspicious files
2. Check logs for PDF extraction or conversion errors
3. Inspect resulting PDF for embedded content
4. Consider full system scan

### If Rate Limit Bypass Detected

1. Check reverse proxy logs for request rate
2. Identify attacker IP range
3. Add WAF or firewall rule to block
4. Review for account enumeration or DoS

## Third-Party Integrations

### OpenAI API

- Endpoint: `https://api.openai.com/v1/chat/completions`
- Data: PDFs sent as text embeddings for analysis
- Privacy: Subject to OpenAI privacy policy; data retained for API logging

**Recommendation:** Review OpenAI DPA if processing sensitive documents.

### Gemini API (Optional)

- Similar considerations as OpenAI
- Endpoint: `https://generativelanguage.googleapis.com/`

## Compliance

This application does not enforce compliance with any standards (GDPR, HIPAA, PCI-DSS, etc.):

- No audit logging
- No data minimization
- No encryption at rest
- No access controls
- No consent management

**If required for compliance:**
1. Implement proper authentication & authorization
2. Add audit logging
3. Encrypt sensitive data
4. Implement data retention policies
5. Add audit trails for downloads/access
6. Consider third-party compliance layer (OAuth2 proxy with audit logging)

## Security Summary

| Area | Status | Risk |
|------|--------|------|
| Authentication | None | High |
| Authorization | None | High |
| Rate Limiting | IP-based | Medium |
| File Validation | Extension-based | Medium |
| Encryption | Not implemented | Medium-High |
| API Security | Keys in plaintext | Medium |
| LibreOffice Sandboxing | None | Medium |
| Logging | Uncontrolled | Low |

**Overall Assessment:** Suitable for internal/development use only. Production deployment requires additional security controls at proxy/gateway layer.

