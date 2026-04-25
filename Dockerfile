# ══════════════════════════════════════════════════════════════════════════
# Multi-stage build
# Stage 1: Build the JAR using Maven + JDK
# Stage 2: Run the JAR using minimal JRE only
# ══════════════════════════════════════════════════════════════════════════

# ── STAGE 1: BUILD ────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first — Docker caches this layer.
# Dependencies are not re-downloaded unless pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the fat JAR
COPY src ./src
RUN mvn package -DskipTests -B

# ── STAGE 2: RUNTIME ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Run as non-root for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the JAR from build stage — no source, no Maven cache
COPY --from=builder /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]