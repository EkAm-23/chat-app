# ── Stage 1: Build ──────────────────────────────────────────────
FROM gradle:8.7-jdk17 AS build

WORKDIR /app

# Copy Gradle config files first for better layer caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY proto/build.gradle.kts proto/
COPY server/build.gradle.kts server/
COPY client/build.gradle.kts client/

# Copy source code
COPY proto/src proto/src
COPY server/src server/src

# Create minimal client source to satisfy the multi-module build
RUN mkdir -p client/src/main/kotlin && \
    echo "fun main() {}" > client/src/main/kotlin/Placeholder.kt

# Build the server jar
RUN gradle :server:bootJar --no-daemon -x test

# ── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/server/build/libs/chat-server.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
