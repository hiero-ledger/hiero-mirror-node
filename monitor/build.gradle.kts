// SPDX-License-Identifier: Apache-2.0

description = "Hiero Mirror Node Monitor"

plugins {
    id("openapi-conventions")
    id("spring-conventions")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common")) {
        exclude("com.google.protobuf", "protobuf-java")
        exclude("org.springframework.boot", "spring-boot-starter-data-jpa")
        exclude("org.web3j", "core")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.google.guava:guava")
    implementation("com.hedera.hashgraph:sdk")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-inprocess")
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-stub")
    implementation("io.micrometer:micrometer-registry-prometheus") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("io.swagger:swagger-annotations")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-math3")
    implementation("org.springdoc:springdoc-openapi-webflux-ui")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-kubernetes-fabric8-autoconfig")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly(
        group = "io.netty",
        name = "netty-resolver-dns-native-macos",
        classifier = "osx-aarch_64",
    )
    testImplementation("com.github.meanbeanlib:meanbean")
    testImplementation("io.fabric8:kubernetes-server-mock")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("uk.org.webcompere:system-stubs-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
