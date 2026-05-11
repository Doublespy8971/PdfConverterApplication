# Development Guide

## Local Setup

### Prerequisites

- Java 21 (check with `java --version`)
- Maven 3.9+ (check with `mvn --version`)
- Git
- LibreOffice (optional, for office→PDF conversions)

### Install Java 21

**macOS:**
```bash
brew install java
# Or install JDK 21 from Oracle/Temurin
```

**Linux:**
```bash
# Ubuntu/Debian
sudo apt-get install openjdk-21-jdk

# Or using SDKMAN
curl -s "https://get.sdkman.io" | bash
sdk install java 21
```

**Windows:**
Download from Oracle or Temurin.

### Clone & Build

```bash
cd ~/projects  # or your workspace
git clone https://github.com/your-org/PdfConverterApplication-main.git
cd PdfConverterApplication-main

# Build
mvn clean package -DskipTests

# Or with tests
mvn clean package
```

**First build:** ~2-5 minutes (dependency download).

## Running Locally

### Option 1: Maven Spring Boot Plugin

```bash
mvn spring-boot:run
```

Application starts on `http://localhost:8080`.

**Debug logs:** Set via environment:
```bash
export SPRING_LOG_LEVEL_ROOT=DEBUG
mvn spring-boot:run
```

### Option 2: Run JAR

```bash
mvn package -DskipTests
java -jar target/pdf-converter-0.0.1-SNAPSHOT.jar
```

### Option 3: IDE (IntelliJ, Eclipse)

1. Open project in IDE
2. Right-click `PdfConverterApplication.java`
3. Select "Run" or "Debug"
4. Application runs with IDE debugger attached

## IDE Setup

### IntelliJ IDEA

1. **File → Open** → Select project root
2. **IntelliJ** will auto-detect Maven project
3. **Run → Edit Configurations** → Spring Boot:
   ```
   Main class: com.pm.pdfconverterapplication.PdfConverterApplication
   VM options: -Xmx512m
   Environment variables: OPENAI_API_KEY=sk-...
   ```
4. **Run → Run** (Shift+F10)

**Debug:**
- Set breakpoints (left-click line number)
- Run → Debug (Shift+F9)

### VS Code

1. **Extensions:** Install "Extension Pack for Java"
2. **Terminal:**
   ```bash
   code .
   # Or open folder in VS Code
   ```
3. **Debug → Add Configuration → Java → Spring Boot**
4. **Debug → Start Debugging (F5)**

## Project Structure

```
src/
├─ main/
│  ├─ java/com/pm/pdfconverterapplication/
│  │  ├─ PdfConverterApplication.java         # Entry point
│  │  ├─ config/
│  │  │  ├─ AsyncConfig.java                  # Thread pools
│  │  │  ├─ SecurityConfig.java               # CORS, headers, auth
│  │  │  └─ WebConfig.java                    # Web MVC config
│  │  ├─ controller/
│  │  │  ├─ ConverterController.java          # Main REST endpoints
│  │  │  └─ AIController.java                 # AI summarization
│  │  ├─ service/
│  │  │  ├─ ConversionService.java            # Conversion logic
│  │  │  ├─ TaskRegistryService.java          # Task tracking
│  │  │  ├─ AsyncConversionWorker.java        # Async jobs
│  │  │  └─ LibreOfficeConverterService.java  # Office→PDF
│  │  ├─ provider/
│  │  │  ├─ LLMProvider.java                  # Interface
│  │  │  ├─ OpenAIProvider.java               # OpenAI impl
│  │  │  └─ GeminiProvider.java               # Gemini impl
│  │  ├─ interceptor/
│  │  │  └─ RateLimitingInterceptor.java      # Rate limiting
│  │  ├─ model/
│  │  │  └─ SummaryResult.java                # Response model
│  │  ├─ task/
│  │  │  └─ TempFileCleanupTask.java          # Scheduled cleanup
│  │  └─ util/
│  │     └─ FileNameUtils.java                # File validation
│  │
│  └─ resources/
│     ├─ application.properties                # Config
│     └─ static/
│        ├─ index.html                         # Web UI
│        ├─ css/style.css
│        └─ js/main.js
│
├─ test/
│  └─ java/com/pm/pdfconverterapplication/
│     ├─ PdfConverterApplicationTests.java    # Integration tests
│     ├─ service/
│     │  └─ TaskRegistryServiceTest.java
│     └─ util/
│        └─ FileNameUtilsTest.java
│
pom.xml                                        # Maven config
Dockerfile                                     # Docker image
docker-compose.yml                             # Docker Compose
```

## Making Changes

### Adding a Conversion Tool

1. **Update `ConversionService.java`:**

   a. Add tool definition to `TOOLS` map:
   ```java
   static {
       toolsMap.put("my-tool", new ToolDefinition(
           "my-tool",
           "pdf",                                    // output extension
           "application/pdf",                        // MIME type
           Set.of("doc", "docx", "txt")              // input formats
       ));
       // ...
   }
   ```

   b. Implement conversion method:
   ```java
   private ConversionResult convertMyTool(MultipartFile file, ToolDefinition toolDef) 
       throws Exception {
       try (InputStream input = file.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream()) {
           
           // Conversion logic here
           byte[] result = performConversion(input);
           
           output.write(result);
           return new ConversionResult(
               output.toByteArray(),
               buildOutputFileName(file.getOriginalFilename(), "pdf"),
               toolDef.contentType()
           );
       }
   }
   ```

   c. Add route in `convertSingleFile()` switch:
   ```java
   case "my-tool" -> convertMyTool(file, toolDefinition);
   ```

2. **Test locally:**
   ```bash
   mvn spring-boot:run
   # Then POST file to /api/convert/my-tool
   ```

3. **Update documentation:**
   - Add to `README_PROFESSIONAL.md` supported conversions table
   - Add to API reference section

### Modifying Configuration

**Local (development):**

1. Edit `src/main/resources/application.properties`:
   ```properties
   spring.servlet.multipart.max-file-size=50MB  # Reduce for testing
   ```

2. Or set environment variable:
   ```bash
   export SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
   mvn spring-boot:run
   ```

**Docker:**

Edit `docker-compose.yml`:
```yaml
environment:
  - JAVA_OPTS=-Xmx1024m -Xms512m
  - SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
```

### Debugging Issues

#### Breakpoints in IDE

1. Click left margin of line to set breakpoint (red dot appears)
2. Start application in debug mode (F5 or Debug button)
3. Make request that hits breakpoint
4. IDE pauses; use Variables panel to inspect state
5. Step over (F10), step into (F11), continue (F5)

#### Remote Debugging

```bash
# Start app with debug port
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar target/pdf-converter-0.0.1-SNAPSHOT.jar

# In IDE: Run → Edit Configurations → Remote
# Set host=localhost, port=5005
# Debug → Debug 'Remote'
```

#### Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

logger.debug("Debug message: {}", variable);
logger.info("Info message");
logger.warn("Warning: {}", issue);
logger.error("Error occurred", exception);
```

View logs in IDE console or terminal.

**Filter logs:**
```bash
mvn spring-boot:run 2>&1 | grep -i error
mvn spring-boot:run 2>&1 | grep "MyClass"
```

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test

```bash
mvn test -Dtest=TaskRegistryServiceTest
mvn test -Dtest=TaskRegistryServiceTest#testTaskExpiration
```

### Write a New Test

Create `src/test/java/com/pm/pdfconverterapplication/service/MyServiceTest.java`:

```java
package com.pm.pdfconverterapplication.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MyServiceTest {
    
    private MyService myService;
    
    @BeforeEach
    public void setup() {
        myService = new MyService();
    }
    
    @Test
    public void testMyFeature() {
        // Arrange
        String input = "test";
        
        // Act
        String result = myService.process(input);
        
        // Assert
        assert result.equals("expected");
    }
}
```

Run:
```bash
mvn test -Dtest=MyServiceTest
```

### Manual Testing with cURL

```bash
# Upload and convert
TASK_ID=$(curl -s -X POST \
  -F "file=@test.docx" \
  http://localhost:8080/api/convert/word-to-pdf \
  | jq -r '.taskId')

echo "Task ID: $TASK_ID"

# Poll status (wait for completion)
while true; do
  STATUS=$(curl -s http://localhost:8080/api/convert/status/$TASK_ID | jq -r '.status')
  echo "Status: $STATUS"
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 2
done

# Download
curl -O http://localhost:8080/api/convert/download/$TASK_ID
```

### Load Testing

**Using Apache Bench:**
```bash
ab -n 100 -c 10 http://localhost:8080/
```

**Using wrk (better):**
```bash
# Install wrk: brew install wrk (macOS)

wrk -t4 -c100 -d30s http://localhost:8080/
```

**Using siege:**
```bash
siege -c 10 -r 10 http://localhost:8080/api/convert/metrics
```

## Building & Packaging

### Build JAR

```bash
mvn clean package -DskipTests
# Output: target/pdf-converter-0.0.1-SNAPSHOT.jar
```

### Build Docker Image

```bash
docker build -t pdf-converter:latest .
docker run -p 8080:8080 pdf-converter:latest
```

### Build with Maven Profile (Not Implemented)

Can add profiles for different configs (dev, staging, prod):

```xml
<!-- pom.xml -->
<profiles>
    <profile>
        <id>prod</id>
        <properties>
            <maven.compiler.optimize>true</maven.compiler.optimize>
        </properties>
    </profile>
</profiles>
```

Run: `mvn package -Pprod`

## Code Style

### Conventions

- **Naming:**
  - Classes: PascalCase (e.g., `ConversionService`)
  - Methods: camelCase (e.g., `convertFile`)
  - Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRIES`)

- **Format:**
  - Indentation: 4 spaces
  - Line length: 100-120 characters (soft limit)
  - Braces: Opening on same line (K&R style)

- **Logging:**
  - Use `logger.info()` for important events
  - Use `logger.debug()` for detailed diagnostics
  - Never log sensitive data (API keys, file contents)

### Linting

Not configured; consider adding:

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
        <configLocation>google_checks.xml</configLocation>
    </configuration>
</plugin>
```

Run: `mvn checkstyle:check`

## Performance Profiling

### CPU Profiling

Using JProfiler or YourKit:

1. Start application with profiler agent
2. Connect profiler GUI
3. Record CPU samples
4. Analyze hot spots

### Memory Profiling

```bash
# Generate heap dump
jcmd <PID> GC.heap_dump heap.hprof

# Analyze (download Eclipse MAT or similar)
# Upload heap.hprof to analyze memory leaks
```

### Benchmarking

Using JMH (Java Microbenchmark Harness):

```bash
# Add dependency to pom.xml
mvn clean package
java -jar target/benchmarks.jar
```

## CI/CD Integration

### GitHub Actions Example

Create `.github/workflows/build.yml`:

```yaml
name: Build & Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: 21
      - name: Build
        run: mvn clean package -DskipTests
      - name: Test
        run: mvn test
```

## Deployment Pipeline (Manual Example)

```bash
#!/bin/bash

set -e

# 1. Build
mvn clean package -DskipTests

# 2. Tag
git tag v1.0.1
git push origin v1.0.1

# 3. Build Docker image
docker build -t pdf-converter:v1.0.1 .

# 4. Push to registry
docker tag pdf-converter:v1.0.1 myregistry/pdf-converter:v1.0.1
docker push myregistry/pdf-converter:v1.0.1

# 5. Deploy (example: pull and restart)
ssh user@prod.example.com << EOF
  cd /opt/pdf-converter
  docker-compose pull
  docker-compose up -d
EOF

# 6. Verify
curl https://converter.example.com/api/convert/metrics

echo "Deployment successful!"
```

## Troubleshooting Common Issues

### Application won't start: "Address already in use"

```bash
# Find process on port 8080
lsof -i :8080
# Or
netstat -tuln | grep 8080

# Kill it
kill -9 <PID>

# Or change port
export SERVER_PORT=8081
mvn spring-boot:run
```

### Test fails: Cannot find LibreOffice

```bash
# Install LibreOffice
brew install libreoffice  # macOS
sudo apt-get install libreoffice  # Ubuntu

# Or skip tests
mvn package -DskipTests
```

### Build fails: Java version mismatch

```bash
# Check installed Java
java --version

# Should be 21+; if not:
brew install openjdk@21  # macOS
export JAVA_HOME=/path/to/jdk-21
mvn clean package
```

### Strange Maven errors: Clear cache

```bash
# Remove Maven repository cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean package -DskipTests
```

## Next Steps

1. **Make a change:** Edit a file and rebuild
2. **Run tests:** Ensure changes don't break functionality
3. **Test manually:** Verify via UI or cURL
4. **Commit:** `git add . && git commit -m "Description"`
5. **Push:** `git push origin branch-name`
6. **Create PR:** Submit for review

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Maven Guide](https://maven.apache.org/guides/)
- [PDFBox API](https://pdfbox.apache.org/)
- [Docker Documentation](https://docs.docker.com/)
- [JUnit 5 Guide](https://junit.org/junit5/docs/current/user-guide/)

