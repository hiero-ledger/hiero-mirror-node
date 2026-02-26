// SPDX-License-Identifier: Apache-2.0

description = "Mirror Node REST Java"

plugins {
    id("openapi-conventions")
    id("jooq-conventions")
    id("spring-conventions")
}

dependencies {
    annotationProcessor("org.mapstruct:mapstruct-processor")
    implementation(project(":common"))
    implementation("com.hedera.hashgraph:app:0.73.0-alpha.1") {
        exclude(group = "io.netty")
        exclude(group = "io.opentelemetry")
        exclude(group = "io.prometheus")
        exclude(group = "org.assertj")
        exclude("org.junit")
    }
    implementation("com.hedera.hashgraph:app-service-entity-id-impl:0.73.0-alpha.1") {
        exclude(group = "io.netty")
    }
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.mapstruct:mapstruct")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            "-Amapstruct.defaultComponentModel=jakarta",
            "-Amapstruct.defaultInjectionStrategy=constructor",
            "-Amapstruct.disableBuilders=true",
        )
    )
}
