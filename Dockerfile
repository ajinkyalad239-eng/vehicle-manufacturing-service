# syntax=docker/dockerfile:1

# ---------- 1. Build stage: compile and package with Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (`mvn verify`); skip them here to keep image builds fast
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

# ---------- 2. Runtime stage: small JRE image, non-root user ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app

# Copy in layers (least -> most frequently changed) for better Docker layer caching
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001
EXPOSE 8080

# Respect container memory limits; exit on OOM so the orchestrator restarts the pod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
