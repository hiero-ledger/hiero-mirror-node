// SPDX-License-Identifier: Apache-2.0

import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.cyclonedx.bom")
    id("org.springframework.boot")
}

gitProperties { dotGitDirectory = rootDir.resolve(".git") }

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo()
}

tasks.named("dockerBuild") { dependsOn(tasks.bootJar) }

tasks.register("run") { dependsOn(tasks.bootRun) }

val imagePlatform: String by project
val platform = imagePlatform.ifBlank { null }

tasks.bootBuildImage {
    cleanCache = true

    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    if (project.name == "web3") {
        builder.set("ghcr.io/${repo}/web3-builder-libsodium")
    } else {
        buildpacks =
            listOf(
                "urn:cnb:builder:paketo-buildpacks/java",
                "paketobuildpacks/health-checker",
                "paketo-buildpacks/native-image",
            )
    }

    docker {
        imageName = image
        imagePlatform = platform
        publishRegistry {
            password = env.getOrDefault("GITHUB_TOKEN", "")
            username = env.getOrDefault("GITHUB_ACTOR", "")
        }
        tags = listOf("${image}:${project.version}")
    }

    val existingBuildArgs = env.getOrDefault("BP_NATIVE_IMAGE_BUILD_ARGUMENTS", "")
    val extraBuildArgs =
        listOf("-H:ServiceLoaderFeatureExcludeServices=org.hibernate.bytecode.spi.BytecodeProvider")
    val nativeImageBuildArgs =
        (listOf(existingBuildArgs) + extraBuildArgs).filter { it.isNotBlank() }.joinToString(" ")

    environment =
        mapOf(
            "BP_HEALTH_CHECKER_ENABLED" to "true",
            "BP_JVM_VERSION" to "25",
            "BP_NATIVE_IMAGE" to "true",
            "BP_NATIVE_IMAGE_BUILD_ARGUMENTS" to nativeImageBuildArgs,
            "BP_OCI_AUTHORS" to "mirrornode@hedera.com",
            "BP_OCI_DESCRIPTION" to (project.description ?: ""),
            "BP_OCI_LICENSES" to "Apache-2.0",
            "BP_OCI_REF_NAME" to env.getOrDefault("GITHUB_REF_NAME", "main"),
            "BP_OCI_REVISION" to env.getOrDefault("GITHUB_SHA", ""),
            "BP_OCI_SOURCE" to "https://github.com/${repo}",
            "BP_OCI_VENDOR" to "Hiero",
        )
}

// Task must be ran with graal vm
tasks.register<BootRun>("bootRunWithNativeAgent") {
    group = "application"
    description = "Run the Spring Boot app with the GraalVM Native Image tracing agent"

    val bootRun = tasks.named<BootRun>("bootRun").get()

    mainClass.set(bootRun.mainClass)
    classpath = bootRun.classpath
    args = bootRun.args
    jvmArgs =
        (bootRun.jvmArgs ?: emptyList()) +
            listOf(
                "-Dspring.aot.enabled=true",
                "-agentlib:native-image-agent=config-output-dir=${project.projectDir}/src/main/resources/META-INF/native-image/org.hiero.mirror/${project.name}",
            )

    systemProperties.putAll(bootRun.systemProperties)
    environment.putAll(bootRun.environment)
    workingDir = bootRun.workingDir
}
