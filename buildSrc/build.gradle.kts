// SPDX-License-Identifier: Apache-2.0

plugins { `kotlin-dsl` }

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    val dockerJavaVersion = "3.5.1"
    val flywayVersion = "11.9.1"
    val jooqVersion = "3.20.5" // Always make the version in project root build.gradle.kts match

    // Add docker-java dependencies before gradle-docker-plugin to avoid the docker-java jars
    // embedded in the plugin being used by testcontainers-postgresql
    implementation("com.github.docker-java:docker-java-api:$dockerJavaVersion")
    implementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
    implementation("com.bmuschko:gradle-docker-plugin:9.4.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.4")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.0.0-beta13")
    implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
    implementation("com.gorylenko.gradle-git-properties:gradle-git-properties:2.5.0")
    implementation("com.graphql-java-generator:graphql-gradle-plugin3:2.9")
    implementation("gradle.plugin.io.snyk.gradle.plugin:snyk:0.7.0")
    implementation("gradle.plugin.org.flywaydb:gradle-plugin-publishing:$flywayVersion") {
        exclude(group = "org.antlr")
    }
    implementation("io.freefair.gradle:lombok-plugin:8.14")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion") {
        exclude(group = "org.antlr")
    }
    implementation("org.gradle:test-retry-gradle-plugin:1.6.2")
    implementation("org.jooq:jooq-codegen-gradle:$jooqVersion")
    implementation("org.jooq:jooq-meta:$jooqVersion")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.13.0")
    implementation("org.owasp:dependency-check-gradle:12.1.3")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.3")
    implementation("org.testcontainers:postgresql:1.21.2")
    implementation("org.web3j:web3j-gradle-plugin:4.14.0")
}

val gitHook =
    tasks.register<Exec>("gitHook") {
        commandLine("git", "config", "core.hookspath", "buildSrc/src/main/resources/hooks")
    }

project.tasks.build { dependsOn(gitHook) }
