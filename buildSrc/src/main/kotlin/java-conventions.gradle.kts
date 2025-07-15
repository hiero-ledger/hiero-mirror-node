// SPDX-License-Identifier: Apache-2.0

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("common-conventions")
    id("io.freefair.lombok")
    id("io.spring.dependency-management")
    id("jacoco")
    id("java-library")
    id("org.gradle.test-retry")
}

configurations.all {
    exclude(group = "com.github.jnr") // Unused and has licensing issues
    exclude(group = "commons-logging", "commons-logging")
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "org.slf4j", module = "slf4j-nop")
}

repositories { maven { url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven/") } }

dependencyManagement {
    imports {
        val grpcVersion: String by rootProject.extra
        mavenBom("io.grpc:grpc-bom:${grpcVersion}")
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

val mockitoAgent = configurations.register("mockitoAgent")

dependencies {
    annotationProcessor(platform(project(":")))
    implementation(platform(project(":")))
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    // Disable serial and this-escape warnings due to errors in generated code
    options.compilerArgs.addAll(
        listOf("-parameters", "-Werror", "-Xlint:all", "-Xlint:-this-escape,-preview,-deprecation")
    )
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.compileJava { options.compilerArgs.add("-Xlint:-serial") }

tasks.javadoc { options.encoding = "UTF-8" }

tasks.withType<Test>().configureEach {
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs =
        listOf(
            "-javaagent:${mockitoAgent.get().asPath}", // JDK 21 restricts libs attaching agents
            "-XX:+EnableDynamicAgentLoading", // Allow byte buddy for Mockito
        )
    maxHeapSize = "4096m"
    minHeapSize = "1024m"
    systemProperty("user.timezone", "UTC")
    systemProperty("spring.test.constructor.autowire.mode", "ALL")
    systemProperty("spring.main.cloud-platform", "NONE")
    useJUnitPlatform {}
    if (
        System.getenv().containsKey("CI") &&
            !System.getenv().containsKey("HIERO_MIRROR_WEB3_EVM_MODULARIZEDSERVICES")
    ) {
        retry { maxRetries = 3 }
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        xml.required = true
    }
}
