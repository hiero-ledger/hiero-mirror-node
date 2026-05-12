// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.web3j.gradle.plugin.GenerateContractWrappers
import org.web3j.gradle.plugin.Web3jExtension
import org.web3j.solidity.gradle.plugin.SolidityCompile

description = "Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("org.web3j") apply false
    id("org.web3j.solidity") apply false
    id("spring-conventions")
}

configurations.all {
    exclude(group = "io.vertx") // Unused and frequently has vulnerabilities
}

dependencies {
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.hedera.hashgraph:app") {
        exclude(group = "io.netty")
        exclude(group = "io.opentelemetry")
        exclude(group = "io.prometheus")
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
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.web3j:core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val web3jGeneratedPackageName = "org.hiero.mirror.web3.web3j.generated"
val historicalSolidityVersion = "0.8.7"

val requestedTasks = gradle.startParameter.taskNames

val isNativeBuild =
    requestedTasks.any {
        it.contains("native", ignoreCase = true)
        it.contains("bootBuildImage", ignoreCase = true)
    }

val isTestExecution =
    !isNativeBuild &&
        requestedTasks.any {
            it.contains("test", ignoreCase = true)
            it.contains("check", ignoreCase = true)
            it.contains("build", ignoreCase = true)
        }

if (isTestExecution) {

    pluginManager.apply("org.web3j")
    pluginManager.apply("org.web3j.solidity")

    extensions.configure<Web3jExtension>("web3j") {
        generateBoth = true
        generatedPackageName = web3jGeneratedPackageName
        useNativeJavaTypes = true
    }

    sourceSets.register("testHistorical") {
        java { setSrcDirs(listOf("src/testHistorical/java", "src/testHistorical/solidity")) }
        resources { setSrcDirs(listOf("src/testHistorical/resources")) }
        compileClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
    }

    tasks.named("processTestResources") { dependsOn(tasks.withType<GenerateContractWrappers>()) }

    tasks.withType<GenerateContractWrappers>().configureEach {
        dependsOn(tasks.withType<SolidityCompile>())
    }

    tasks.named("extractSolidityImports") {
        val preDefinedPackageJson = layout.projectDirectory.file("src/test/resources/package.json")

        actions.clear()

        doLast {
            @Suppress("UNCHECKED_CAST")
            val packageJson = property("packageJson") as Provider<RegularFile>
            preDefinedPackageJson.asFile.copyTo(packageJson.get().asFile, overwrite = true)
        }
    }

    val resolveSolidity = tasks.named("resolveSolidity")

    tasks.named<SolidityCompile>("compileTestHistoricalSolidity") {
        dependsOn(resolveSolidity)

        group = "historical"
        resolvedImports.set(
            providers.provider {
                val getAllImports =
                    resolveSolidity.get().javaClass.methods.single { it.name == "getAllImports" }
                val allImports = getAllImports.invoke(resolveSolidity.get()) as RegularFileProperty
                allImports.get()
            }
        )

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

    tasks.named("compileTestJava") { dependsOn(moveTestHistoricalFiles) }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.add("-Xlint:-unchecked") // Web3j generates code with unchecked
    options.compilerArgs.removeIf { it == "-Werror" }
}

tasks.withType<JavaExec>().configureEach { jvmArgs = listOf("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }
