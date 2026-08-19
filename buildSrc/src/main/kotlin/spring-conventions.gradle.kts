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

val imagePlatform = project.property("imagePlatform") as String
val platform = imagePlatform.ifBlank { null }

tasks.bootBuildImage {
    // Use digests for deterministic builds.
    val builderImageDigest =
        "sha256:6b3fa425bdbe81a766f2349265cf13aec4a070a0c6a77947b996df3ce7fa6cbd" // 0.0.172
    val nativeImageDigest =
        "sha256:f2e20d6de81f161b36573f786715a908ffc43c12d69b7a35f566e1573de3c75e" // 14.9.0
    val runImageDigest =
        "sha256:5a405dda46956555107baec6df6a2d469c88a7e67573d9dced8bc52bd27c13c5" // 0.0.116

    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    builder = "paketobuildpacks/builder-noble-java-tiny@${builderImageDigest}"
    buildpacks = listOf("paketobuildpacks/java-native-image@${nativeImageDigest}")
    runImage = "paketobuildpacks/ubuntu-noble-run-tiny@${runImageDigest}"

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
            "-J-Xmx10g", // Try to prevent CI runner's OOM crashes.
        )
    val nativeImageBuildArgs = extraBuildArgs.filter { it.isNotBlank() }.joinToString(" ")

    environment =
        mapOf(
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
