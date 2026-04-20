# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime image with LibreOffice
FROM eclipse-temurin:21-jdk-jammy

# Install LibreOffice and required dependencies for headless conversion
RUN apt-get update && apt-get install -y \
    libreoffice \
    libreoffice-writer \
    libreoffice-calc \
    libreoffice-impress \
    libreoffice-common \
    libreoffice-java-common \
    ure \
    default-jre \
    fonts-liberation \
    fonts-dejavu \
    libcairo2 \
    libxinerama1 \
    libxrandr2 \
    libglu1-mesa \
    libxext6 \
    libx11-6 \
    libsm6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Create tmp directory for LibreOffice
RUN mkdir -p /tmp && chmod 1777 /tmp

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/pdf-converter-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health 2>/dev/null || exit 1

# Set environment variables
ENV HOME=/tmp
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

