# =================================================================
# Stage 1: Build Java Spring Boot Application with Maven
# =================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests

# =================================================================
# Stage 2: Runtime Image (Ultra Lightweight Alpine JRE)
# =================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled JAR from builder stage
COPY --from=builder /app/target/payslip-sender-1.0.0.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# Expose port (Render environment will set PORT dynamically)
ENV PORT=8080
EXPOSE ${PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/ || exit 1

# Entry point
ENTRYPOINT ["java", "-jar", "app.jar"]
