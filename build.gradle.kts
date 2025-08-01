// SPDX-License-Identifier: Apache-2.0

import com.github.gradle.node.npm.task.NpmSetupTask
import java.nio.file.Paths

description = "Hedera Mirror Node imports data from consensus nodes and serves it via an API"

plugins {
    id("com.diffplug.spotless")
    id("com.github.node-gradle.node")
    id("idea")
    id("java-platform")
    id("snykcode-extension")
}

// Can't use typed variable syntax due to Dependabot limitations
extra.apply {
    set("blockNodeVersion", "0.14.0")
    set("commons-lang3.version", "3.18.0") // Temporary until next Spring Boot
    set("grpcVersion", "1.73.0")
    set("jooq.version", "3.20.5") // Must match buildSrc/build.gradle.kts
    set("mapStructVersion", "1.6.3")
    set("nodeJsVersion", "22.17.1")
    set("protobufVersion", "4.31.1")
    set("reactorGrpcVersion", "1.2.4")
    set("tuweniVersion", "2.3.1")
}

// Creates a platform/BOM with specific versions so subprojects don't need to specify a version when
// using a dependency
dependencies {
    constraints {
        val blockNodeVersion: String by rootProject.extra
        val grpcVersion: String by rootProject.extra
        val mapStructVersion: String by rootProject.extra
        val protobufVersion: String by rootProject.extra
        val reactorGrpcVersion: String by rootProject.extra
        val tuweniVersion: String by rootProject.extra

        api("com.asarkar.grpc:grpc-test:2.0.0")
        api("com.esaulpaugh:headlong:13.3.1")
        api("com.github.meanbeanlib:meanbean:3.0.0-M9")
        api("com.github.vertical-blank:sql-formatter:2.0.5")
        api("org.bouncycastle:bcprov-jdk18on:1.81")
        api("com.bucket4j:bucket4j-core:8.10.1")
        api("com.google.guava:guava:33.4.8-jre")
        api("com.google.protobuf:protobuf-java:$protobufVersion")
        api("com.graphql-java-generator:graphql-java-client-runtime:2.9")
        api("com.graphql-java:graphql-java-extended-scalars:24.0")
        api("com.graphql-java:graphql-java-extended-validation:24.0")
        api("com.hedera.hashgraph:app:0.64.1")
        api("com.hedera.evm:hedera-evm:0.54.2")
        api("com.hedera.hashgraph:hedera-protobuf-java-api:0.63.5")
        api("com.hedera.hashgraph:sdk:2.61.0")
        api("com.ongres.scram:client:2.1")
        api("com.salesforce.servicelibs:reactor-grpc-stub:$reactorGrpcVersion")
        api("commons-beanutils:commons-beanutils:1.11.0")
        api("commons-io:commons-io:2.20.0")
        api("io.cucumber:cucumber-bom:7.23.0")
        api("io.github.mweirauch:micrometer-jvm-extras:0.2.2")
        api("io.grpc:grpc-bom:$grpcVersion")
        api("io.hypersistence:hypersistence-utils-hibernate-63:3.10.3")
        api("io.projectreactor:reactor-core-micrometer:1.2.8")
        api("io.swagger:swagger-annotations:1.6.16")
        api("io.vertx:vertx-core:4.5.16") // Temporary until next Spring Boot
        api("io.vertx:vertx-web:4.5.16") // Temporary until next Spring Boot
        api("io.vertx:vertx-web-client:4.5.16") // Temporary until next Spring Boot
        api("jakarta.inject:jakarta.inject-api:2.0.1")
        api("javax.inject:javax.inject:1")
        api("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")
        api("net.java.dev.jna:jna:5.17.0")
        api("org.apache.commons:commons-collections4:4.5.0")
        api("org.apache.commons:commons-compress:1.27.1")
        api("org.apache.commons:commons-math3:3.6.1")
        api("org.apache.tuweni:tuweni-bytes:$tuweniVersion")
        api("org.apache.tuweni:tuweni-units:$tuweniVersion")
        api("org.apache.velocity:velocity-engine-core:2.4.1")
        api("org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api:5.0.2")
        api("org.gaul:s3proxy:2.6.0")
        api("org.hiero.block:block-node-protobuf-sources:$blockNodeVersion")
        api("org.hyperledger.besu:secp256k1:0.8.2")
        api("org.hyperledger.besu:evm:24.3.3")
        api("org.mapstruct:mapstruct:$mapStructVersion")
        api("org.mapstruct:mapstruct-processor:$mapStructVersion")
        api("org.msgpack:jackson-dataformat-msgpack:0.9.10")
        api("org.springdoc:springdoc-openapi-webflux-ui:1.8.0")
        api("org.mockito:mockito-inline:5.2.0")
        api("org.web3j:core:4.12.2")
        api("software.amazon.awssdk:bom:2.32.9")
        api("tech.pegasys:jc-kzg-4844:1.0.0")
        api("uk.org.webcompere:system-stubs-jupiter:2.1.8")
    }
}

allprojects { apply(plugin = "jacoco") }

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// Spotless uses Prettier and it requires Node.js
node {
    val nodeJsVersion: String by rootProject.extra
    download = true
    version = nodeJsVersion
    workDir = rootDir.resolve(".gradle").resolve("nodejs")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

spotless {
    val licenseHeader = "// SPDX-License-Identifier: Apache-2.0\n\n"

    val npmExec =
        when (System.getProperty("os.name").lowercase().contains("windows")) {
            true -> Paths.get("npm.cmd")
            else -> Paths.get("bin", "npm")
        }
    val npmSetup = tasks.named("npmSetup").get() as NpmSetupTask
    val npmExecutable = npmSetup.npmDir.get().asFile.toPath().resolve(npmExec)

    isEnforceCheck = false

    if (!System.getenv().containsKey("CI")) {
        ratchetFrom("origin/main")
    }

    format("go") {
        endWithNewline()
        licenseHeader(licenseHeader, "package")
        target("rosetta/**/*.go")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
    }
    format("helm") {
        endWithNewline()
        leadingTabsToSpaces(2)
        licenseHeader(licenseHeader.replaceFirst("//", "#"), "^[a-zA-Z0-9{]+")
        target("charts/**/*.yaml", "charts/**/*.yml")
        trimTrailingWhitespace()
    }
    format("javascript") {
        endWithNewline()
        leadingTabsToSpaces(2)
        licenseHeader(licenseHeader, "$")
        prettier()
            .npmExecutable(npmExecutable)
            .npmInstallCache(Paths.get("${rootProject.rootDir}", ".gradle", "spotless"))
            .config(mapOf("bracketSpacing" to false, "printWidth" to 120, "singleQuote" to true))
        target("rest/**/*.js", "tools/**/*.js")
        targetExclude(
            "**/build/**",
            "**/node_modules/**",
            "**/__tests__/integration/*.spec.test.js",
            "tools/mirror-report/index.js",
        )
    }
    java {
        endWithNewline()
        licenseHeader(licenseHeader, "package")
        palantirJavaFormat()
        removeUnusedImports()
        target("**/*.java")
        targetExclude(
            "**/build/**",
            "rest/**",
            "rosetta/**",
            // Known issue with Java 21: https://github.com/palantir/palantir-java-format/issues/933
            "rest-java/**/EntityServiceImpl.java",
            "tools/**",
        )
        toggleOffOn()
        trimTrailingWhitespace()
    }
    kotlin {
        endWithNewline()
        ktfmt().kotlinlangStyle()
        licenseHeader(licenseHeader, "package")
        target("buildSrc/**/*.kt")
        targetExclude("**/build/**")
    }
    kotlinGradle {
        endWithNewline()
        ktfmt().kotlinlangStyle()
        licenseHeader(licenseHeader, "(description|import|plugins)")
        target("*.kts", "*/*.kts", "buildSrc/**/*.kts", "rest/*/*.kts")
        targetExclude("**/build/**", "**/node_modules/**")
        trimTrailingWhitespace()
    }
    format("miscellaneous") {
        endWithNewline()
        leadingTabsToSpaces(2)
        prettier()
            .npmExecutable(npmExecutable)
            .npmInstallCache(Paths.get("${rootProject.rootDir}", ".gradle", "spotless"))
        target("**/*.json", "**/*.md")
        targetExclude(
            "**/build/**",
            "**/charts/**/dashboards/**",
            "**/node_modules/**",
            "**/package-lock.json",
        )
        trimTrailingWhitespace()
    }
    format("proto") {
        endWithNewline()
        leadingTabsToSpaces(4)
        licenseHeader(licenseHeader, "(package|syntax)")
        target("protobuf/**/*.proto")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
    }
    format("shell") {
        endWithNewline()
        leadingTabsToSpaces(2)
        licenseHeader("#!/usr/bin/env bash\n\n" + licenseHeader.replaceFirst("//", "#"), "^[^#\\s]")
        target("**/*.sh")
        targetExclude("**/build/**", "**/node_modules/**")
        trimTrailingWhitespace()
    }
    sql {
        endWithNewline()
        leadingTabsToSpaces()
        licenseHeader(licenseHeader.replaceFirst("//", "--"), "^[^-\\s]")
        target(
            "common/src/test/resources/*.sql",
            "importer/**/*.sql",
            "rest/__tests__/data/**/*.sql",
        )
        targetExclude("**/build/**", "**/db/migration/**")
        trimTrailingWhitespace()
    }
    format("yaml") {
        endWithNewline()
        leadingTabsToSpaces(2)
        prettier()
            .npmExecutable(npmExecutable)
            .npmInstallCache(Paths.get("${rootProject.rootDir}", ".gradle", "spotless"))
        licenseHeader(licenseHeader.replaceFirst("//", "#"), "^[a-zA-Z0-9{]+")
        target("**/*.yaml", "**/*.yml")
        targetExclude("**/build/**", "charts/**", "**/node_modules/**")
        trimTrailingWhitespace()
    }
}

fun replaceVersion(files: String, match: String) {
    ant.withGroovyBuilder {
        "replaceregexp"("match" to match, "replace" to project.version, "flags" to "gm") {
            "fileset"(
                "dir" to rootProject.projectDir,
                "includes" to files,
                "excludes" to "**/node_modules/",
            )
        }
    }
}

tasks.nodeSetup { onlyIf { !this.nodeDir.get().asFile.exists() } }

tasks.register("release") {
    description = "Replaces release version in files."
    group = "release"
    doLast {
        replaceVersion("charts/**/Chart.yaml", "(?<=^(appVersion|version): ).+")
        replaceVersion("docker-compose.yml", "(?<=gcr.io/mirrornode/hedera-mirror-.+:).+")
        replaceVersion("gradle.properties", "(?<=^version=).+")
        replaceVersion(
            "rest/**/package*.json",
            "(?<=\"@hiero-ledger/(check-state-proof|mirror-rest|mirror-monitor)\",\\s{3,7}\"version\": \")[^\"]+",
        )
        replaceVersion("rest/**/openapi.yml", "(?<=^  version: ).+")
        replaceVersion(
            "tools/traffic-replay/log-downloader/package*.json",
            "(?<=\"@hiero-ledger/mirror-log-downloader\",\\s{3,7}\"version\": \")[^\"]+",
        )
    }
}

tasks.spotlessApply { dependsOn(tasks.nodeSetup) }

tasks.spotlessCheck { dependsOn(tasks.nodeSetup) }
