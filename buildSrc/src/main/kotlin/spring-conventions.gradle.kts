// SPDX-License-Identifier: Apache-2.0

import org.springframework.boot.gradle.tasks.aot.ProcessAot
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

// Spring Data AOT walks persistent types with ReflectionUtils; JDK internals (Throwable.backtrace,
// FilterInputStream.in,
// Reference.referent, etc.) require explicit opens on Java 16+ when third-party types subclass JDK
// classes (protobuf).
tasks.withType<ProcessAot>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )

    // Spring Data 4.0 generates bytecode property accessors (Entity__Accessor classes) that get/set
    // fields through MethodHandles. In a native image, setting a primitive boolean field through
    // them
    // hits a GraalVM bug (oracle/graal#12596 / GR-71699) and fails with "Can not set boolean field
    // ...
    // to java.lang.Integer". Disable them so Spring Data falls back to reflection-based access,
    // which
    // also keeps importer ingestion on par with the original JPA based solution.
    // TODO(GR-71699): drop the flag once the native builder ships the graal#13042 fix.
    jvmArgs("-Dspring.aot.data.accessors.enabled=false")
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
        "sha256:1a3c25ee49a18a499bd7657222c81bcda84f450622a54d24a7b25b80a9ce6593" // 0.1.70
    val nativeImageDigest =
        "sha256:60486e497d2554626f621fef1ab8866acd23328541d0f5bc34d2689a5630af0c" // 14.15.1
    val runImageDigest =
        "sha256:d3626bf14fd4234ae2f096ef2129ea52f1cc7e4d30331a9124e6dcd6d1297649" // 0.0.53
    val syftDigest =
        "sha256:7652f36f22d3da51a41dadc77994a6a8172f374c6005f0c95318007c15d5ec9d" // 2.44.1

    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    builder = "paketobuildpacks/ubuntu-resolute-builder@${builderImageDigest}"
    // Listed first so detection keeps 2.43.2 and skips the broken copy inside the composite.
    buildpacks =
        listOf(
            "paketobuildpacks/syft@${syftDigest}",
            "paketobuildpacks/java-native-image@${nativeImageDigest}",
        )
    runImage = "paketobuildpacks/ubuntu-resolute-run-tiny@${runImageDigest}"

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
