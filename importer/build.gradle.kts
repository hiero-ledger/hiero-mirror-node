// SPDX-License-Identifier: Apache-2.0

import com.google.protobuf.gradle.id

description = "Mirror Node Importer"

plugins {
    id("com.google.protobuf")
    id("spring-conventions")
}

dependencies {
    val blockNodeVersion: String by rootProject.extra

    implementation(platform("software.amazon.awssdk:bom"))
    implementation(project(":common"))
    implementation("com.esaulpaugh:headlong")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")
    implementation("commons-io:commons-io")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.projectreactor:reactor-core")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("net.java.dev.jna:jna")
    implementation("org.apache.commons:commons-compress")
    implementation("org.apache.commons:commons-collections4")
    implementation("org.apache.velocity:velocity-engine-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hyperledger.besu:secp256k1")
    implementation("org.msgpack:jackson-dataformat-msgpack")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.retry:spring-retry")
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")
    protobuf("org.hiero.block:block-node-protobuf-sources:$blockNodeVersion")
    runtimeOnly("io.grpc:grpc-netty")
    runtimeOnly(
        group = "io.netty",
        name = "netty-resolver-dns-native-macos",
        classifier = "osx-aarch_64",
    )
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("com.asarkar.grpc:grpc-test")
    testImplementation("com.github.vertical-blank:sql-formatter")
    testImplementation("commons-beanutils:commons-beanutils")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.apache.commons:commons-math3")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api") // Used by s3proxy
    testImplementation("org.gaul:s3proxy") {
        exclude(group = "org.apache.commons", module = "commons-fileupload2-javax")
    }
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    val protobufVersion: String by rootProject.extra

    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java" } }
    generateProtoTasks {
        ofSourceSet("main").forEach { it.plugins { id("grpc") { option("@generated=omit") } } }
    }
}
