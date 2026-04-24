# ─── Stage 1: Build ───────────────────────────────────────────
# Full JDK + Maven to compile and package the fat jar
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first so Docker caches dependencies separately from source
# This means a code change won't re-download all dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Run ─────────────────────────────────────────────
# Slim JRE only — no compiler, no Maven, smaller attack surface
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*-fat.jar app.jar

EXPOSE 8080

# Crash fast if the service is unhealthy rather than silently failing
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8084/health

ENTRYPOINT ["java", "-jar", "app.jar"]
