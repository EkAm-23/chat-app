# Chat App

## Features

- 🔐 User registration with duplicate detection
- 💬 Real-time 1:1 messaging via gRPC server-streaming
- 🟢 Live online/offline status indicators
- 📦 Offline message queuing & delivery on reconnect
- 📜 Message history loading
- 📋 Recent conversations list

## Demo
https://github.com/user-attachments/assets/8af366d9-e162-424a-af68-809cc6baaa23

# Build and Run Instructions

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

### Build

```bash
# Build Docker images for server and client
docker compose build

build

# Or build them individually
docker compose build server
docker compose build client
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
./gradlew :client:run --console=plain
```
