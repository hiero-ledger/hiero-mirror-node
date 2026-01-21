// SPDX-License-Identifier: Apache-2.0

import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("common-conventions")
    id("io.freefair.lombok")
    id("io.spring.dependency-management")
    id("jacoco")
    id("java-library")
    id("net.ltgt.errorprone")
    id("org.gradle.test-retry")
}

configurations.all {
    exclude(group = "com.nimbusds") // Unused and has a vulnerability
    exclude(group = "com.github.jnr") // Unused and has licensing issues
    exclude(group = "commons-logging", "commons-logging")
    exclude(group = "org.apache.logging.log4j", module = "log4j-core")
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
    // Versions for errorprone don't seem to work when only specified in root build.gradle.kts
    errorprone("com.google.errorprone:error_prone_core:2.42.0")
    errorprone("com.uber.nullaway:nullaway:0.12.10")
    implementation(platform(project(":")))
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    // Disable dangling-doc-comments due to graphql-gradle-plugin-project #25
    // Disable serial and this-escape warnings due to errors in generated code
    // Disable rawtypes and unchecked due to Spring AOT generated configuration
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Werror",
            "-Xlint:all",
            "-Xlint:-dangling-doc-comments,-preview,-rawtypes,-this-escape,-unchecked",
        )
    )
    options.encoding = "UTF-8"
    options.errorprone {
        disableAllChecks = true
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:CustomContractAnnotations", "org.springframework.lang.Contract")
    }
    sourceCompatibility = "25"
    targetCompatibility = "25"
}

tasks.compileJava { options.compilerArgs.add("-Xlint:-serial") }

tasks.javadoc { options.encoding = "UTF-8" }

tasks.withType<Test>().configureEach {
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs =
        listOf(
            "-javaagent:${mockitoAgent.get().asPath}", // JDK 21+ restricts libs attaching agents
            "-XX:+EnableDynamicAgentLoading", // Allow byte buddy for Mockito
        )
    maxHeapSize = "4096m"
    minHeapSize = "1024m"
    systemProperty("user.timezone", "UTC")
    systemProperty("spring.test.constructor.autowire.mode", "ALL")
    systemProperty("spring.main.cloud-platform", "NONE")
    useJUnitPlatform {}
    if (System.getenv().containsKey("CI")) {
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
