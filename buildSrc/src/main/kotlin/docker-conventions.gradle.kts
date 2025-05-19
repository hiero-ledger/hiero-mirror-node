// SPDX-License-Identifier: Apache-2.0

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins { id("com.bmuschko.docker-remote-api") }

val latest = "latest"

// Get the Docker images to tag by splitting dockerTag property and adding the project version
fun dockerImages(): Collection<String> {
    val dockerRegistry: String by project
    val dockerTag: String by project
    val dockerImage = "${dockerRegistry}/hedera-mirror-${projectDir.name}:"
    val customTags = dockerTag.split(',').map { dockerImage.plus(it) }
    val versionTag = dockerImage.plus(project.version)
    val tags = customTags.plus(versionTag).toMutableSet()

    // Don't tag pre-release versions as latest
    if (tags.contains(latest) && project.version.toString().contains('-')) {
        tags.remove(latest)
    }

    return tags.toList()
}

val dockerBuild =
    tasks.register<DockerBuildImage>("dockerBuild") {
        onlyIf { projectDir.resolve("Dockerfile").exists() }
        buildArgs.put("VERSION", project.version.toString())
        images.addAll(dockerImages())
        inputDir = file(projectDir)
        pull = true

        val dockerPlatform: String by project
        if (dockerPlatform.isNotBlank()) {
            buildArgs.put("TARGETPLATFORM", dockerPlatform)
            platform = dockerPlatform
        }
    }

tasks.register<DockerPushImage>("dockerPush") {
    onlyIf { projectDir.resolve("Dockerfile").exists() }
    dependsOn(dockerBuild)
    images.addAll(dockerImages())
}
