// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.cyclonedx.bom")
    id("org.graalvm.buildtools.native")
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
    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}/${project.name}"

    buildpacks =
        listOf(
            "urn:cnb:builder:paketo-buildpacks/java",
            "paketobuildpacks/health-checker",
            "paketo-buildpacks/native-image",
        )
    docker {
        imageName = image
        imagePlatform = platform
        publishRegistry {
            password = env.getOrDefault("GITHUB_TOKEN", "")
            username = env.getOrDefault("GITHUB_ACTOR", "")
        }
        tags = listOf("${image}:${project.version}")
    }
    environment =
        mapOf(
            "BP_HEALTH_CHECKER_ENABLED" to "true",
            "BP_OCI_AUTHORS" to "mirrornode@hedera.com",
            "BP_OCI_DESCRIPTION" to (project.description ?: ""),
            "BP_OCI_LICENSES" to "Apache-2.0",
            "BP_OCI_REF_NAME" to env.getOrDefault("GITHUB_REF_NAME", "main"),
            "BP_OCI_REVISION" to env.getOrDefault("GITHUB_SHA", ""),
            "BP_OCI_SOURCE" to "https://github.com/${repo}",
            "BP_OCI_VENDOR" to "Hiero",
        )
}
