FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ca-certificates && update-ca-certificates
WORKDIR /app

COPY --from=builder /app/target/*-fat.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8081/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
