// SPDX-License-Identifier: Apache-2.0

description = "Hedera Mirror Node Common"

plugins { id("java-conventions") }

dependencies {
    val testClasses by configurations.creating
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.github.ben-manes.caffeine:caffeine")
    api("com.google.guava:guava")
    api("com.google.protobuf:protobuf-java")
    api("com.hedera.hashgraph:hedera-protobuf-java-api") { isTransitive = false }
    api("io.hypersistence:hypersistence-utils-hibernate-63")
    api("commons-codec:commons-codec")
    api("org.apache.commons:commons-lang3")
    api("org.apache.tuweni:tuweni-bytes")
    api("org.apache.tuweni:tuweni-units")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.web3j:core")
    api("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.hyperledger.besu:evm")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework:spring-web")

    testClasses(sourceSets["test"].output)
}

java.sourceSets["main"].java { srcDir("build/generated/sources/annotationProcessor/java/main") }
