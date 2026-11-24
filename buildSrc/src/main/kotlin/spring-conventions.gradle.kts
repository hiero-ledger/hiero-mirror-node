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
        "sha256:96678722f0d61f6102ceccd83f829a935a304fc353bbd5b20bbbfd90c28533b8" // 0.0.222
    val nativeImageDigest =
        "sha256:60486e497d2554626f621fef1ab8866acd23328541d0f5bc34d2689a5630af0c" // 14.15.1
    val runImageDigest =
        "sha256:34e5d30227697b12a5d6d484aeee44662f288640e15bc36d1165f932bef56853" // 0.0.163
    val syftDigest =
        "sha256:7652f36f22d3da51a41dadc77994a6a8172f374c6005f0c95318007c15d5ec9d" // 2.44.1

    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    builder = "paketobuildpacks/builder-noble-java-tiny@${builderImageDigest}"
    // Listed first so detection keeps 2.43.2 and skips the broken copy inside the composite.
    buildpacks =
        listOf(
            "paketobuildpacks/syft@${syftDigest}",
            "paketobuildpacks/java-native-image@${nativeImageDigest}",
        )
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
            "-H:+SharedArenaSupport", // For Netty used via AWS SDK
            "-H:+StripDebugInfo",
            "-H:+UnlockExperimentalVMOptions",
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
                "-agentlib:native-image-agent=config-output-dir=${project.projectDir}/src/main/resources/META-INF/native-image/org.hiero.mirror/${project.name}",
            )
    mainClass.set(bootRun.mainClass)
    systemProperties.putAll(bootRun.systemProperties)
    workingDir = bootRun.workingDir
}
