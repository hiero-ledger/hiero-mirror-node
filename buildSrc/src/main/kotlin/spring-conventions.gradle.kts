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
        "sha256:93f09329da5a6e8c2644cf5ea12de165064d72358cc7de5d8bac6d1c886335cf" // 0.0.204
    val nativeImageDigest =
        "sha256:4049925127996485d9fb1c2741c1788c061e5e5ba8d95ce15aed58f6beda3f3e" // 14.14.2
    val runImageDigest =
        "sha256:b5aacecc8b049f54f683ff988db2ea0ae9a523873c12b15296ebbd39c6fb115a" // 0.0.153
    val syftDigest =
        "sha256:756ba4e4ca66d96ba73c79cec7a375b62177ee2bef1a800ab51ce59a047b9e39" // 2.43.4

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
