// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.springframework.boot")
}

gitProperties { dotGitDirectory = rootDir.resolve(".git") }

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo()
}

tasks.named("dockerBuild") { dependsOn(tasks.bootJar) }

tasks.register("run") { dependsOn(tasks.bootRun) }

tasks.bootBuildImage {
    val env = System.getenv()
    val repo = env.getOrDefault("GITHUB_REPOSITORY", "hiero-ledger/hiero-mirror-node")
    val image = "ghcr.io/${repo}"

    docker {
        imageName = image
        tags = listOf("${image}:${project.version}")
        publishRegistry { token = env["GITHUB_TOKEN"] }
    }

    environment =
        mapOf(
            "BP_NATIVE_IMAGE_BUILD_ARGUMENTS" to
                "--initialize-at-build-time=org.slf4j.helpers.Reporter,org.slf4j.LoggerFactory,ch.qos.logback",
            "BP_OCI_AUTHORS" to "mirrornode@hedera.com",
            "BP_OCI_DESCRIPTION" to project.description,
            "BP_OCI_LICENSES" to "Apache-2.0",
            "BP_OCI_REF_NAME" to env.getOrDefault("GITHUB_REF_NAME", "main"),
            "BP_OCI_REVISION" to env.getOrDefault("GITHUB_SHA", ""),
            "BP_OCI_SOURCE" to "https://github.com/${repo}",
            "BP_OCI_VENDOR" to "Hiero",
        )
}
