# Quickstart

## Local Run
1. Install Java 21 and Maven 3.9+.
2. Install LibreOffice (required for Office-to-PDF conversions).
3. Build the project:
   ```bash
   ./mvnw clean package -DskipTests
   ```
4. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```
5. Open http://localhost:8080.

## Docker Run
```bash
docker-compose up -d --build
```
Then open http://localhost:8080.
