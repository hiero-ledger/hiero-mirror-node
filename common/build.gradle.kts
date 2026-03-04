// SPDX-License-Identifier: Apache-2.0

description = "Hedera Mirror Node Common"

plugins { id("java-conventions") }

configurations.all {
    exclude(group = "io.vertx") // Unused and frequently has vulnerabilities
}

dependencies {
    val testClasses by configurations.registering
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")
    api("com.github.ben-manes.caffeine:caffeine")
    api("com.google.guava:guava")
    api("com.google.protobuf:protobuf-java")
    api("com.hedera.hashgraph:hedera-protobuf-java-api") { isTransitive = false }
    api("commons-codec:commons-codec")
    api("io.hypersistence:hypersistence-utils-hibernate-71")
    api("jakarta.servlet:jakarta.servlet-api")
    api("org.apache.commons:commons-lang3")
    api("org.apache.tuweni:tuweni-bytes")
    api("org.apache.tuweni:tuweni-units")
    api("org.slf4j:jcl-over-slf4j")
    api("org.springframework.boot:spring-boot-jackson2")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-micrometer-metrics")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.web3j:core")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.hyperledger.besu:evm")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")

    testClasses(sourceSets["test"].output)
}

java.sourceSets["main"].java { srcDir("build/generated/sources/annotationProcessor/java/main") }
