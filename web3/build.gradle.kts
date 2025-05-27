// SPDX-License-Identifier: Apache-2.0

import org.web3j.gradle.plugin.GenerateContractWrappers
import org.web3j.solidity.gradle.plugin.SolidityCompile
import org.web3j.solidity.gradle.plugin.SolidityExtractImports
import org.web3j.solidity.gradle.plugin.SolidityResolve

description = "Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("org.web3j")
    id("org.web3j.solidity")
    id("spring-conventions")
}

repositories {
    // Temporary repository added for com.hedera.cryptography snapshot dependencies
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.esaulpaugh:headlong")
    implementation("com.hedera.hashgraph:app") { exclude(group = "io.netty") }
    implementation("com.hedera.evm:hedera-evm")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("javax.inject:javax.inject")
    implementation("net.java.dev.jna:jna")
    implementation("org.bouncycastle:bcprov-jdk18on")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.vertx:vertx-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.mockito:mockito-inline")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.webjars.npm:openzeppelin__contracts:4.9.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val web3jGeneratedPackageName = "org.hiero.mirror.web3.web3j.generated"

web3j {
    generateBoth = true
    generatedPackageName = web3jGeneratedPackageName
    useNativeJavaTypes = true
}

val historicalSolidityVersion = "0.8.7"
val latestSolidityVersion = "0.8.25"

// Define "testHistorical" source set needed for the test historical solidity contracts and web3j
sourceSets {
    val testHistorical by creating {
        java { setSrcDirs(listOf("src/testHistorical/java", "src/testHistorical/solidity")) }
        resources { setSrcDirs(listOf("src/testHistorical/resources")) }
        compileClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        solidity { version = historicalSolidityVersion }
    }
    test { solidity { version = latestSolidityVersion } }
}

val extractContracts =
    tasks.register<Copy>("extractContracts") {
        description = "Extracts the OpenZeppelin dependencies into the configured output folder"
        group = "historical"
        from({
            configurations.testCompileClasspath
                .get()
                .filter { it.name.contains("openzeppelin__contracts") }
                .map { zipTree(it) }
        })
        into(layout.projectDirectory.asFile.resolve("src/testHistorical/solidity/openzeppelin"))
        include("openzeppelin-contracts-*/contracts/**/*.sol")
        eachFile { path = path.replace("openzeppelin-contracts-.+/contracts", "") }
    }

tasks.bootRun { jvmArgs = listOf("--enable-preview") }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }

tasks.openApiGenerate { mustRunAfter(tasks.withType<SolidityResolve>()) }

tasks.processTestResources { dependsOn(tasks.withType<GenerateContractWrappers>()) }

tasks.withType<SolidityExtractImports> { dependsOn(extractContracts) }

tasks.withType<GenerateContractWrappers> { dependsOn(tasks.withType<SolidityCompile>()) }

afterEvaluate {
    tasks.named("compileTestHistoricalSolidity", SolidityCompile::class.java).configure {
        group = "historical"
        allowPaths = setOf("src/testHistorical/solidity/openzeppelin")
        ignoreMissing = true
        version = historicalSolidityVersion
        source = fileTree("src/testHistorical/solidity") { include("*.sol") }
    }
}

val moveTestHistoricalFiles =
    tasks.register<Copy>("moveTestHistoricalFiles") {
        description = "Move files from testHistorical to test"
        group = "historical"

        val baseDir = layout.buildDirectory.dir("generated/sources/web3j").get()
        val subDir = "java/" + web3jGeneratedPackageName.replace('.', '/')
        val srcDir = baseDir.dir("testHistorical").dir(subDir).asFile
        val destDir = baseDir.dir("test").dir(subDir).asFile

        from(srcDir) { include("**/*Historical.java") }
        into(destDir)
        dependsOn(tasks.withType<GenerateContractWrappers>())
    }

tasks.compileTestJava {
    options.compilerArgs.add("-Xlint:-unchecked") // Web3j generates code with unchecked
    options.compilerArgs.removeIf { it == "-Werror" }
    dependsOn(moveTestHistoricalFiles)
}
