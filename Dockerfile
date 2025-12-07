# =====================================================
# ChatGPT Web Server Dockerfile
# Multi-stage build for optimized image size
# =====================================================

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy source files
COPY src/ ./src/
COPY sql/ ./sql/

# Download PostgreSQL JDBC driver
RUN wget -O postgresql.jar https://jdbc.postgresql.org/download/postgresql-42.7.1.jar

# Compile Java source
RUN mkdir -p out && \
    find src -name "*.java" > sources.txt && \
    javac -d out -cp postgresql.jar @sources.txt

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 chatbot && \
    adduser -u 1001 -G chatbot -s /bin/sh -D chatbot

# Copy compiled classes and dependencies
COPY --from=builder /app/out/ ./out/
COPY --from=builder /app/postgresql.jar ./lib/
COPY --from=builder /app/sql/ ./sql/

# Copy static files and config
COPY public/ ./public/
COPY config/ ./config/

# Create directories for logs
RUN mkdir -p logs && chown -R chatbot:chatbot /app

# Switch to non-root user
USER chatbot

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Environment variables (defaults, override in docker-compose)
ENV SERVER_PORT=8080 \
    DATABASE_URL=jdbc:postgresql://db:5432/chatbot \
    DATABASE_USER=chatbot \
    DATABASE_PASSWORD=chatbot

# Run the application
CMD ["java", "-cp", "out:lib/postgresql.jar", "server.WebServer"]
