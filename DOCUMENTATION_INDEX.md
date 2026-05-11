# PDF Converter Application - Documentation Index

This document serves as a navigation guide to all project documentation.

## Quick Links

| Document | Purpose | Audience |
|----------|---------|----------|
| **README_PROFESSIONAL.md** | Main project overview, setup, usage, API reference | Everyone |
| **ARCHITECTURE.md** | Technical design, data models, concurrency model | Developers, Architects |
| **DEVELOPMENT.md** | Local setup, building, testing, code style | Developers |
| **OPERATIONS.md** | Deployment, configuration, monitoring, troubleshooting | DevOps, SREs |
| **SECURITY.md** | Security model, vulnerabilities, hardening checklist | Security, DevOps |

---

## For Different Roles

### Getting Started (New Developer)

1. Read: **README_PROFESSIONAL.md** → Overview
2. Follow: **DEVELOPMENT.md** → Local Setup
3. Code: Build locally, explore codebase
4. Test: Run `mvn test`, try manual testing

### Writing Code (Feature Development)

1. Review: **ARCHITECTURE.md** → System design
2. Check: **DEVELOPMENT.md** → Code style, testing
3. Implement: Add feature following conventions
4. Test: Unit tests + manual testing
5. Document: Update README if API changes

### Deploying to Production (DevOps)

1. Review: **OPERATIONS.md** → Deployment options
2. Check: **SECURITY.md** → Hardening checklist
3. Configure: Set environment, reverse proxy
4. Deploy: Choose Docker, Kubernetes, or JAR
5. Monitor: Set up logging, metrics, alerts

### Security Review (Security Team)

1. Read: **SECURITY.md** → Full threat model
2. Review: **ARCHITECTURE.md** → Data flow
3. Check: **DEVELOPMENT.md** → Dependencies
4. Validate: Run tests, check logs
5. Harden: Follow checklist, configure proxy

### Troubleshooting (Support)

1. Check: **OPERATIONS.md** → Troubleshooting section
2. Logs: `docker-compose logs pdf-converter`
3. Metrics: `curl http://localhost:8080/api/convert/metrics`
4. Test: Manual API calls with cURL
5. Escalate: Reference ARCHITECTURE if deeper investigation needed

---

## Document Structure

### README_PROFESSIONAL.md (19 KB)

Comprehensive overview covering:

- **Sections:**
  - Overview & status
  - Architecture diagram
  - Supported conversions table
  - Setup (local and Docker)
  - Usage (web UI and REST API)
  - Configuration reference
  - Complete API documentation
  - Known limitations (critical reading)
  - Performance characteristics
  - Deployment models
  - Troubleshooting
  - Future improvements

- **Best for:** First read for anyone; reference for API docs

- **Key Topics:**
  - In-memory task storage (no persistence)
  - LibreOffice resource requirements
  - PDF conversion fidelity limitations
  - Rate limiting model
  - Scaling limitations

### ARCHITECTURE.md (15 KB)

Deep technical dive covering:

- **Sections:**
  - System architecture diagram
  - Request lifecycle (step-by-step)
  - Data models (TaskStatus, ConversionResult)
  - Concurrency model (thread pools, race conditions)
  - Memory management (heap sizing, GC)
  - File handling (upload flow, cleanup)
  - Conversion strategies (PDFBox vs LibreOffice)
  - Rate limiting internals
  - Async processing queue model
  - Dependency injection
  - Configuration loading
  - Monitoring & observability
  - Testing approach
  - Future architecture changes

- **Best for:** Developers needing to understand internals; architects evaluating scalability

- **Key Topics:**
  - HTTP thread pool vs worker pool
  - ConcurrentHashMap task registry
  - LibreOffice subprocess management
  - Temporary file lifecycle
  - Memory leak prevention

### DEVELOPMENT.md (13 KB)

Hands-on guide for local development:

- **Sections:**
  - Prerequisites & installation
  - Running locally (3 options: Maven, JAR, IDE)
  - Project structure walkthrough
  - Making changes (step-by-step examples)
  - Debugging techniques
  - Testing (unit tests, manual, load testing)
  - Building & packaging
  - Code style conventions
  - Performance profiling
  - CI/CD integration examples
  - Troubleshooting common issues

- **Best for:** First-time setup; day-to-day development; onboarding

- **Key Topics:**
  - IDE configuration (IntelliJ, VS Code)
  - Adding new conversion tools
  - Breakpoint debugging
  - Running tests
  - Load testing with wrk

### SECURITY.md (10 KB)

Security assessment and hardening:

- **Sections:**
  - Authentication & authorization (none)
  - Rate limiting evaluation
  - File upload security
  - LibreOffice integration risks
  - CORS configuration
  - API security (CSRF, headers)
  - AI provider integration security
  - Data protection (in-transit, at-rest)
  - Dependency CVEs
  - Deployment hardening checklist
  - Incident response procedures
  - Third-party integrations (OpenAI, Gemini)
  - Compliance gaps (GDPR, HIPAA, PCI)
  - Security summary table

- **Best for:** Security teams; production deployments; compliance reviews

- **Key Topics:**
  - No authentication (design choice)
  - API keys in plaintext (mitigation: env vars)
  - LibreOffice CVE exposure
  - TLS enforcement (reverse proxy)
  - Hardening checklist (13 items)

### OPERATIONS.md (17 KB)

Production deployment & operations:

- **Sections:**
  - Pre-deployment checklist
  - Deployment options (JAR, Docker, Kubernetes)
  - Configuration management (env vars, systemd, nginx)
  - Performance tuning (heap, threads, caching)
  - Monitoring & observability (logging, metrics, alerts)
  - Backup & recovery strategy
  - Operational tasks (logs, scaling, updates)
  - Compliance & auditing
  - Disaster scenarios (5 scenarios with fixes)
  - Capacity planning
  - Cost estimation (AWS pricing)
  - Change management & deployment script

- **Best for:** DevOps, SREs, operators; production deployments

- **Key Topics:**
  - Systemd service configuration
  - nginx reverse proxy setup
  - Prometheus metrics collection
  - Scaling horizontal limitations
  - RTO/RPO for single-instance
  - Zero-downtime deployment

---

## Common Questions

### Q: Where do I start?

**A:** Start with **README_PROFESSIONAL.md**, sections 1-3. Then choose your path:
- Developer: Read **DEVELOPMENT.md** for local setup
- DevOps: Read **OPERATIONS.md** for deployment
- Security: Read **SECURITY.md** for threat model

### Q: How do I add a new feature?

**A:** Read **DEVELOPMENT.md** → "Making Changes" section. Then:
1. Make changes locally
2. Run tests: `mvn test`
3. Update **README_PROFESSIONAL.md** if API changes
4. Commit with clear message

### Q: How do I deploy to production?

**A:** Read **OPERATIONS.md** → "Deployment Options". Choose one:
1. **Docker Compose:** Simplest, single-instance
2. **Kubernetes:** Production-grade, high-availability
3. **JAR + Systemd:** Minimal setup

Then follow **SECURITY.md** → "Hardening Checklist" before going live.

### Q: What are the limitations?

**A:** Read **README_PROFESSIONAL.md** → "Known Limitations" section:
- In-memory task storage (no horizontal scaling)
- LibreOffice resource requirements (limited concurrency)
- PDF conversion fidelity (text-only, layout lost)
- File size constraints (100MB per file)

### Q: Why did my conversion fail?

**A:** See **OPERATIONS.md** → "Troubleshooting Production Issues":
1. Check logs: `docker-compose logs pdf-converter | grep ERROR`
2. Check metrics: `curl http://localhost:8080/api/convert/metrics`
3. Review error type in **OPERATIONS.md** troubleshooting section

### Q: How do I secure this for production?

**A:** Read **SECURITY.md** entirely, then:
1. Deploy behind TLS-terminating reverse proxy (nginx)
2. Set CORS to your domain only
3. Use environment variables for API keys
4. Enable rate limiting at proxy level
5. Set up logging aggregation & alerting
6. Follow **SECURITY.md** → "Hardening Checklist"

### Q: What's the maximum throughput?

**A:** See **README_PROFESSIONAL.md** → "Performance Characteristics" and **OPERATIONS.md** → "Capacity Planning":
- Single instance: 4-8 concurrent conversions
- Horizontal scaling: Not supported without external state store
- Recommended: Start small, monitor metrics, scale based on demand

### Q: How do I monitor the application?

**A:** See **OPERATIONS.md** → "Monitoring & Observability":
1. Health: `curl http://localhost:8080/health`
2. Metrics: `curl http://localhost:8080/api/convert/metrics`
3. Logs: `docker-compose logs pdf-converter`
4. Advanced: Prometheus, ELK, or CloudWatch integration

### Q: What if the application crashes?

**A:** See **OPERATIONS.md** → "Disaster Scenarios":
- **Single-instance:** Restart (in-progress tasks lost)
- **Multiple instances:** Failed instance replaced automatically
- **Data loss:** No persistent state; users resubmit files

### Q: Can I scale horizontally?

**A:** Not currently. See **README_PROFESSIONAL.md** → "Scaling Considerations":
- Each instance has independent in-memory task registry
- Requires external state store (Redis, database) to share tasks
- Temporary files must be on shared filesystem (NFS, S3)

---

## File Sizes & Sections Count

| Document | Size | Sections | Typical Read Time |
|----------|------|----------|-------------------|
| README_PROFESSIONAL.md | 19 KB | 15 major | 20-30 min |
| ARCHITECTURE.md | 15 KB | 13 major | 30-40 min |
| DEVELOPMENT.md | 13 KB | 12 major | 20-30 min |
| OPERATIONS.md | 17 KB | 14 major | 25-35 min |
| SECURITY.md | 10 KB | 11 major | 15-25 min |

---

## Documentation Maintenance

When updating code:

1. **API changes:** Update **README_PROFESSIONAL.md** → API Reference
2. **Architecture changes:** Update **ARCHITECTURE.md** → affected sections
3. **Deployment changes:** Update **OPERATIONS.md** → relevant sections
4. **Security issues:** Update **SECURITY.md** → add to Known Vulnerabilities
5. **New feature:** Add to **README_PROFESSIONAL.md** → Supported Conversions
6. **Setup changes:** Update **DEVELOPMENT.md** → Prerequisites/Setup

---

## Documentation Philosophy

This documentation follows these principles:

1. **Honesty:** No marketing language; realistic limitations stated clearly
2. **Completeness:** Cover normal cases and edge cases
3. **Practical:** Include examples, commands, configurations
4. **Organized:** Sections grouped by role/use case
5. **Maintainable:** Easy to update as code changes
6. **Accessible:** Technical but not overly complex; clear explanations

---

## Related Documentation

In project root:

- `pom.xml` - Maven configuration, dependency versions
- `Dockerfile` - Container image definition, LibreOffice setup
- `docker-compose.yml` - Local development stack
- `src/main/resources/application.properties` - Default configuration
- `FULL_CODEBASE_EXPORT.txt` - Complete source code listing

---

## Getting Help

1. **Check docs:** Search in relevant .md file (Ctrl+F)
2. **Check logs:** See OPERATIONS.md Troubleshooting section
3. **Search code:** Grep source in `/src/main/java`
4. **Run tests:** `mvn test` to verify functionality
5. **Ask community:** Open issue on GitHub or internal channel

---

## Version History

This documentation is current as of:
- **Spring Boot:** 3.2.4
- **Java:** 21
- **PDF Converter:** v0.0.1-SNAPSHOT

Check `pom.xml` and Git history for latest versions.

