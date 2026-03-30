plugins {
    kotlin("jvm")
    application
}

dependencies {
    // Project dependency on proto module
    implementation(project(":proto"))

    // gRPC
    implementation("io.grpc:grpc-netty-shaded:${property("grpcVersion")}")
    implementation("io.grpc:grpc-kotlin-stub:${property("grpcKotlinVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("com.chatapp.client.ChatClientApplicationKt")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.chatapp.client.ChatClientApplicationKt"
    }
}

// Use `./gradlew :client:installDist` to create a runnable distribution
// in build/install/client/ with bin/ (start scripts) and lib/ (all jars).
// This avoids fat-jar META-INF/services merging issues with gRPC.
