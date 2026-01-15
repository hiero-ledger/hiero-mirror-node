// SPDX-License-Identifier: Apache-2.0

import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy

description = "Mirror Node Acceptance Test"

plugins {
    id("com.gradleup.shadow")
    id("docker-conventions")
    id("java-conventions")
    id("openapi-conventions")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(platform("io.cucumber:cucumber-bom"))
    implementation("io.cucumber:cucumber-java")
    implementation("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("com.esaulpaugh:headlong")
    testImplementation("com.google.guava:guava")
    testImplementation("com.hedera.hashgraph:sdk")
    testImplementation(project(":common")) {
        exclude("com.hedera.hashgraph", "hedera-protobuf-java-api")
        exclude("com.google.protobuf", "protobuf-java")
        exclude("org.springframework.boot", "spring-boot-starter-data-jpa")
        exclude("org.web3j", "core")
    }
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.grpc:grpc-okhttp")
    testImplementation("jakarta.inject:jakarta.inject-api")
    testImplementation("net.java.dev.jna:jna")
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.retry:spring-retry")
    testImplementation("org.apache.tuweni:tuweni-bytes")
    testImplementation("commons-codec:commons-codec")
    testImplementation("org.web3j:core")
}

// Disable the default test task and only run acceptance tests during the standalone "acceptance"
// task
tasks.named("test") { enabled = false }

val maxParallelism = project.property("maxParallelism") as String
val test by testing.suites.existing(JvmTestSuite::class)

tasks.register<Test>("acceptance") {
    classpath = files(test.map { it.sources.runtimeClasspath })
    description = "Acceptance tests configuration"
    group = "verification"
    jvmArgs = listOf("-Xmx1024m", "-Xms1024m")
    maxParallelForks =
        if (maxParallelism.isNotBlank()) maxParallelism.toInt()
        else Runtime.getRuntime().availableProcessors()
    testClassesDirs = files(test.map { it.sources.output.classesDirs })
    useJUnitPlatform {}
    doFirst {
        // Copy relevant system properties to the forked test process
        System.getProperties()
            .filter { it.key.toString().matches(Regex("^(cucumber|hedera|hiero|spring)\\..*")) }
            .forEach { systemProperty(it.key.toString(), it.value) }
    }
}

tasks.build { dependsOn("shadowJar") }

tasks.shadowJar {
    dependsOn(tasks.compileTestJava)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(sourceSets.main.get().output)
    from(sourceSets.test.get().output)
    configurations =
        listOf(
            project.configurations.runtimeClasspath.get(),
            project.configurations.testRuntimeClasspath.get(),
        )
    manifest { attributes["Main-Class"] = "org.hiero.mirror.test.TestApplication" }
    mergeServiceFiles()
    append("META-INF/spring.handlers")
    append("META-INF/spring.schemas")
    append("META-INF/spring.tooling")
    val transformer = PropertiesFileTransformer(project.objects)
    transformer.mergeStrategy = MergeStrategy.from("append")
    transformer.paths = listOf("META-INF/spring.factories")
    transform(transformer)
}

tasks.dockerBuild { dependsOn(tasks.shadowJar) }
