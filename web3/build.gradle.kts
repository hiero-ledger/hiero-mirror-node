// SPDX-License-Identifier: Apache-2.0

import com.github.gradle.node.npm.task.NpmInstallTask
import org.web3j.gradle.plugin.GenerateContractWrappers
import org.web3j.solidity.gradle.plugin.SolidityCompile
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
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots") }
}

dependencies {
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
    implementation("org.springframework.retry:spring-retry")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.vertx:vertx-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.mockito:mockito-inline")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
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
    val testHistorical by registering {
        java { setSrcDirs(listOf("src/testHistorical/java", "src/testHistorical/solidity")) }
        resources { setSrcDirs(listOf("src/testHistorical/resources")) }
        compileClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        solidity {
            resolvePackages = false
            version = historicalSolidityVersion
        }
    }
    test { solidity { version = latestSolidityVersion } }
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

afterEvaluate {
    val nodeDir = project.layout.buildDirectory

    val copyPackageJson =
        tasks.register<Copy>("copyPackageJson") {
            from("src/test/resources/package.json")
            into(nodeDir)
        }

    val npmInstall = tasks.named(NpmInstallTask.NAME) { dependsOn(copyPackageJson) }

    val solidityResolve =
        tasks.register<SolidityResolve>("solidityResolve") {
            description = "Resolve external Solidity contract modules."
            packageJson = nodeDir.file("package.json")
            nodeModules = nodeDir.dir("node_modules")
            allImports = nodeDir.file("sol-imports-all.txt")
            dependsOn(npmInstall)
        }

    tasks.withType<SolidityCompile>().configureEach {
        resolvedImports = solidityResolve.flatMap { it.allImports }
        notCompatibleWithConfigurationCache(
            "See https://github.com/LFDT-web3j/web3j-solidity-gradle-plugin/issues/85"
        )
    }

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

tasks.compileTestJava { dependsOn(moveTestHistoricalFiles) }

tasks.processResources { dependsOn(tasks.named("copyPackageJson")) }
