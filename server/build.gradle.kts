plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Project dependency on proto module
    implementation(project(":proto"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Kotlin Reflect — required by Spring Data JPA for Kotlin data classes
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // gRPC Spring Boot Starter
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")

    // gRPC + Kotlin Coroutines
    implementation("io.grpc:grpc-kotlin-stub:${property("grpcKotlinVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("chat-server.jar")
}
