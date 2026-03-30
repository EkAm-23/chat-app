# Chat App

# Build and Run Instructions

**.gitignore, IDE files, and build artifacts are excluded.**

## Chat App — gRPC Real-Time Messaging

A 1:1 real-time chat application built with:
- **gRPC** (with Kotlin coroutines) for communication
- **Spring Boot** for the server
- **PostgreSQL** for persistence
- **Kotlin** throughout
- **Gradle** multi-module build
- **Docker Compose** for orchestration

## Quick Start

### Prerequisites
- Docker & Docker Compose
- JDK 17 (only for local development, not needed for Docker)

### Build

```bash
# Build Docker images for server and client
docker compose build

# Or build them individually
docker compose build server
docker compose build client
```

To build locally with Gradle (without Docker):

```bash
# Build all modules (proto → server → client)
./gradlew build -x test

# Build only proto stubs
./gradlew :proto:build

# Build server bootJar
./gradlew :server:bootJar

# Build client distribution (bin/ + lib/)
./gradlew :client:installDist
```

### Run

```bash
# Start the database and server
docker compose up -d postgres server

# Wait for server to be ready (check logs)
docker compose logs -f server

# In a new terminal — start a chat client (User 1)
docker compose run client

# In another terminal — start another client (User 2)
docker compose run client
```

### Stop

```bash
docker compose down
docker compose down -v  # also removes database volume
```

## Project Structure

```
chat-app/
├── proto/          # Shared protobuf definitions & generated stubs
├── server/         # Spring Boot gRPC server with JPA/PostgreSQL
├── client/         # Standalone Kotlin console client
├── docker-compose.yml
└── build.gradle.kts
```

## Features

- 🔐 User registration with duplicate detection
- 💬 Real-time 1:1 messaging via gRPC server-streaming
- 🟢 Live online/offline status indicators
- 📦 Offline message queuing & delivery on reconnect
- 📜 Message history loading
- 📋 Recent conversations list

## Architecture

```
Client ──gRPC──> Server ──JPA──> PostgreSQL
                   │
                   ├── Register (unary)
                   ├── SendMessage (unary)
                   ├── Subscribe (server-streaming)
                   ├── GetRecentChats (unary)
                   └── GetMessageHistory (unary)
```

## Development

```bash
# Build all modules locally (requires JDK 17)
./gradlew build

# Run server locally (requires PostgreSQL on localhost:5432)
./gradlew :server:bootRun

# Run client locally
./gradlew :client:run --console=plain
```
