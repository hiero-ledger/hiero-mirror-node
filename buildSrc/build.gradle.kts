// SPDX-License-Identifier: Apache-2.0

plugins { `kotlin-dsl` }

repositories { gradlePluginPortal() }

dependencies {
    val dockerJavaVersion = "3.7.0"
    val jooqVersion = "3.20.11" // Always make the version in project root build.gradle.kts match

    // Add docker-java dependencies before gradle-docker-plugin to avoid the docker-java jars
    // embedded in the plugin being used by testcontainers-postgresql
    implementation("com.github.docker-java:docker-java-api:$dockerJavaVersion")
    implementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
    implementation("com.bmuschko:gradle-docker-plugin:9.4.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.2.1")
    implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
    implementation("com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.7")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.1")
    implementation("com.graphql-java-generator:graphql-gradle-plugin3:4.0")
    implementation("gradle.plugin.io.snyk.gradle.plugin:snyk:0.7.0")
    implementation("io.freefair.gradle:lombok-plugin:9.2.0")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.0.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.cyclonedx:cyclonedx-gradle-plugin:3.0.1")
    implementation("org.flywaydb:flyway-database-postgresql:12.0.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.11.4")
    implementation("org.gradle:test-retry-gradle-plugin:1.6.4")
    implementation("org.jooq:jooq-codegen-gradle:$jooqVersion")
    implementation("org.jooq:jooq-meta:$jooqVersion")
    implementation("org.jooq:jooq-postgres-extensions:${jooqVersion}")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.20.0")
    implementation("org.owasp:dependency-check-gradle:12.2.0")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.3")
    implementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    implementation("org.web3j:web3j-gradle-plugin:5.0.2")
}

val gitHook =
    tasks.register<Exec>("gitHook") {
        commandLine("git", "config", "core.hookspath", "buildSrc/src/main/resources/hooks")
    }

tasks.processResources { dependsOn(gitHook) }
