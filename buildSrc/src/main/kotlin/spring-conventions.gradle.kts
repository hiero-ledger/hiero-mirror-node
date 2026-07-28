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

    // Spring Data 4.0 generates bytecode property accessors (Entity__Accessor classes). In a native
    // image, setting a primitive `boolean` field through the generated MethodHandle setter hits a
    // GraalVM bug (oracle/graal#12596 / GR-71699, fixed on master via graal#13042 but not yet in a
    // released builder) that fails with "Can not set boolean field ... to java.lang.Integer".
    // Disabling generated accessors falls back to reflection-based access, which is unaffected.
    // TODO(GR-71699): drop the flag once the native builder ships the graal#13042 fix.
    if (project.name == "importer") {
        jvmArgs("-Dspring.aot.data.accessors.enabled=false")
    }
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
        "sha256:f96e4afa31f8ed4626d3eecc133ced78ff38b9429c18b010a66374920e72ff5e" // 0.0.161
    val nativeImageDigest =
        "sha256:f4b1cf317d9917a42486a40d26b0864cd5235dc23b406663df166a04fdab501a" // 14.8.0
    val runImageDigest =
        "sha256:0cf96ce719096433ff2bfe53953f784f9619ee6a541f7b75bbfa6bebad07b04a" // 0.0.105

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
