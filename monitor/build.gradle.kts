// SPDX-License-Identifier: Apache-2.0

description = "Hiero Mirror Node Monitor"

plugins {
    id("openapi-conventions")
    id("org.graalvm.buildtools.native")
    id("spring-conventions")
}

dependencies {
    implementation(platform("io.fabric8:kubernetes-client-bom"))
    implementation(project(":common")) { isTransitive = false }
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.google.guava:guava")
    implementation("com.hedera.hashgraph:sdk")
    implementation("io.fabric8:kubernetes-client") {
        exclude("io.fabric8", "kubernetes-httpclient-vertx")
    }
    implementation("io.fabric8:kubernetes-httpclient-jdk")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-inprocess")
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-stub")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-math3")
    implementation("org.slf4j:jcl-over-slf4j")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework.boot:spring-boot-starter-micrometer-metrics")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("com.github.meanbeanlib:meanbean")
    testImplementation("io.fabric8:kubernetes-server-mock")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("uk.org.webcompere:system-stubs-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
