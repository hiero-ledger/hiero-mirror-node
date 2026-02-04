// SPDX-License-Identifier: Apache-2.0

import org.web3j.gradle.plugin.GenerateContractWrappers
import org.web3j.solidity.gradle.plugin.SolidityCompile

description = "Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("org.web3j")
    id("org.web3j.solidity")
    id("spring-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.hedera.hashgraph:app") {
        exclude(group = "io.netty")
        exclude(group = "io.opentelemetry")
        exclude(group = "io.prometheus")
        exclude(group = "io.vertx")
        exclude(group = "org.assertj")
        exclude("org.junit")
    }
    implementation("com.hedera.hashgraph:app-service-entity-id-impl") {
        exclude(group = "io.netty")
    }
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("net.java.dev.jna:jna")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val web3jGeneratedPackageName = "org.hiero.mirror.web3.web3j.generated"

web3j {
    generateBoth = true
    generatedPackageName = web3jGeneratedPackageName
    useNativeJavaTypes = true
}

val historicalSolidityVersion = "0.8.7"

// Define "testHistorical" source set needed for the test historical solidity contracts and web3j
sourceSets.register("testHistorical") {
    java { setSrcDirs(listOf("src/testHistorical/java", "src/testHistorical/solidity")) }
    resources { setSrcDirs(listOf("src/testHistorical/resources")) }
    compileClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
    solidity { version = historicalSolidityVersion }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.add("-Xlint:-unchecked") // Web3j generates code with unchecked
    options.compilerArgs.removeIf { it == "-Werror" }
}

tasks.withType<JavaExec>().configureEach { jvmArgs = listOf("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }

tasks.processTestResources { dependsOn(tasks.withType<GenerateContractWrappers>()) }

tasks.withType<GenerateContractWrappers> { dependsOn(tasks.withType<SolidityCompile>()) }

tasks.extractSolidityImports {
    val preDefinedPackageJson = layout.projectDirectory.file("src/test/resources/package.json")
    actions
        .clear() // instead of generating the package.json here, we provide our own predefined one
    doLast { preDefinedPackageJson.asFile.copyTo(packageJson.get().asFile, overwrite = true) }
}

tasks.named<SolidityCompile>("compileTestHistoricalSolidity") {
    group = "historical"
    resolvedImports = tasks.resolveSolidity.flatMap { it.allImports }
    allowPaths = setOf("src/testHistorical/solidity/openzeppelin")
    ignoreMissing = true
    version = historicalSolidityVersion
    source = fileTree("src/testHistorical/solidity") { include("*.sol") }
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

tasks.compileTestJava { dependsOn(moveTestHistoricalFiles) }
