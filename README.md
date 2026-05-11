# PDF Converter Application

This repository contains a Spring Boot service for document conversions (PDF ↔ Office formats, images, and common PDF operations) and optional AI-based summarization. The project is suitable for development, testing and small single-node deployments. The codebase includes an API, an HTML/JS UI, and Docker configuration for local testing.

Important constraints (read before deploying):

- Task state is stored in memory (JVM heap). Tasks and results are lost on restart and the service does not support distributed task tracking.
- LibreOffice-based conversions are resource intensive and spawn external processes; plan memory and CPU accordingly.
- The application is intentionally unauthenticated. For production use, deploy it behind an authenticated reverse proxy or API gateway and follow the hardening checklist in `SECURITY.md`.

See `README_PROFESSIONAL.md` for full documentation and operational guidance.

Quick links

- Start here: `START_HERE.md`
- Complete docs: `README_PROFESSIONAL.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`, `OPERATIONS.md`, `SECURITY.md`, `DOCUMENTATION_INDEX.md`
- Build: `mvn clean package -DskipTests`
- Run (dev): `mvn spring-boot:run`
- Docker (dev): `docker-compose up -d`

If you want me to replace the existing marketing README with the full professional README content instead of this landing page, I can do that. I can also open a branch and create a commit with these documentation files if you'd like.
