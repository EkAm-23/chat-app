import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    id("com.google.protobuf")
}

dependencies {
    implementation("io.grpc:grpc-netty-shaded:${property("grpcVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
    implementation("io.grpc:grpc-kotlin-stub:${property("grpcKotlinVersion")}")
    implementation("com.google.protobuf:protobuf-kotlin:${property("protocVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Required for @Generated annotation
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${property("protocVersion")}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${property("grpcVersion")}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${property("grpcKotlinVersion")}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

// Make generated sources visible to IDE
sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/grpc",
                "build/generated/source/proto/main/grpckt",
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/kotlin"
            )
        }
    }
}
