# =============================================================================
# PharmaSafe Backend Dockerfile (Multi-stage build for Render)
# Runtime: Eclipse Temurin OpenJDK 17
# Base system: Alpine Linux (lightweight, secure)
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Maven Build Stage
# -----------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:resolve -B

# Copy application source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime Stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create unprivileged application user and group for container security
RUN addgroup -S pharmasafe && adduser -S pharmasafe -G pharmasafe

# Copy compiled executable JAR from builder stage
COPY --from=builder /app/target/pharmasafe-backend-0.0.1-SNAPSHOT.jar app.jar

# Enforce secure ownership
RUN chown -R pharmasafe:pharmasafe /app

# Switch to non-root user
USER pharmasafe

# Expose default port (Render injects dynamic $PORT at runtime)
EXPOSE 8080

# Production container defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx384m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Launch Spring Boot via exec to ensure PID 1 receives SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
