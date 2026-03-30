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
COPY client/src client/src

# Create minimal server source to satisfy the multi-module build
RUN mkdir -p server/src/main/kotlin && \
    echo "fun main() {}" > server/src/main/kotlin/Placeholder.kt && \
    mkdir -p server/src/main/resources

# Build the client distribution (bin/ + lib/ with separate jars)
RUN gradle :client:installDist --no-daemon -x test

# ── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the full distribution (start script + all jars kept separate)
COPY --from=build /app/client/build/install/client/ ./

ENTRYPOINT ["./bin/client"]
