// SPDX-License-Identifier: Apache-2.0

import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.cyclonedx.bom")
    id("org.springframework.boot")
}

if (project.name != "graphql") {
    apply(plugin = "org.graalvm.buildtools.native")
    // This slows down tests too much to keep enabled
    tasks.named("processTestAot") { enabled = false }
}

gitProperties { dotGitDirectory = rootDir.resolve(".git") }

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo { excludes = listOf("time") }
}

tasks.named("dockerBuild") { dependsOn(tasks.bootJar) }

tasks.register("run") {
    dependsOn(tasks.bootRun)
    group = "application"
}

val imagePlatform: String by project
val platform = imagePlatform.ifBlank { null }

tasks.bootBuildImage {
    // Use digests for deterministic builds.
    val builderImageDigest =
        "sha256:3c12804ab2b1fd77df0297052c0024cc7915295bc159ed91f19d2321a133a5be" // 0.0.124
    val nativeImageDigest =
        "sha256:f2b2a03e04266d512eb026afe8fab4726955d4557fd3429a46d4644d61d9c55a" // 14.3.0
    val runImageDigest =
        "sha256:18d21d36f4caa29dfcefc38045af61020dd76222d3e76d61f9f0433ac9ad28de" // 0.0.74

    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    builder = "paketobuildpacks/builder-noble-java-tiny@${builderImageDigest}"
    runImage = "paketobuildpacks/ubuntu-noble-run-tiny@${runImageDigest}"
    buildpacks = listOf("paketobuildpacks/java-native-image@${nativeImageDigest}")

    docker {
        imageName = image
        imagePlatform = platform
        publishRegistry {
            password = env.getOrDefault("GITHUB_TOKEN", "")
            username = env.getOrDefault("GITHUB_ACTOR", "")
        }
        tags = listOf("${image}:${project.version}")
    }

    val extraBuildArgs =
        listOf(
            "--enable-compression",
            "-H:NativeLinkerOption=-s",
            "-H:ServiceLoaderFeatureExcludeServices=org.hibernate.bytecode.spi.BytecodeProvider",
            "-H:+StripDebugInfo",
            "-O3",
        )
    val nativeImageBuildArgs = extraBuildArgs.filter { it.isNotBlank() }.joinToString(" ")

    environment =
        mapOf(
            "BP_BINARY_COMPRESSION_METHOD" to "upx",
            "BP_JVM_JLINK_ENABLED" to "true",
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

// Task must be run with GraalVM
tasks.register<BootRun>("bootRunWithNativeAgent") {
    val bootRun = tasks.named<BootRun>("bootRun").get()

    args = bootRun.args
    classpath = bootRun.classpath
    description = "Run the Spring Boot app with the GraalVM Native Image tracing agent"
    environment.putAll(bootRun.environment)
    group = "application"
    jvmArgs =
        bootRun.jvmArgs +
            listOf(
                "-agentlib:native-image-agent=config-output-dir=${project.projectDir}/src/main/resources/META-INF/native-image/org.hiero.mirror/${project.name}"
            )
    mainClass.set(bootRun.mainClass)
    systemProperties.putAll(bootRun.systemProperties)
    workingDir = bootRun.workingDir
}
