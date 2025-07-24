// SPDX-License-Identifier: Apache-2.0

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins { id("com.bmuschko.docker-remote-api") }

val latest = "latest"

// Get the images to tag by splitting imageTag property and adding the project version
fun imageTags(): Collection<String> {
    val imageRegistry: String by project
    val imageTag: String by project
    val image = "${imageRegistry}/hedera-mirror-${project.name}:"
    val customTags = imageTag.split(',').map { image.plus(it) }
    val versionTag = image.plus(project.version)
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
        images.addAll(imageTags())
        inputDir = file(projectDir)
        pull = true

        val imagePlatform: String by project
        if (imagePlatform.isNotBlank()) {
            buildArgs.put("TARGETPLATFORM", imagePlatform)
            platform = imagePlatform
        }
    }

tasks.register<DockerPushImage>("dockerPush") {
    onlyIf { projectDir.resolve("Dockerfile").exists() }
    dependsOn(dockerBuild)
    images.addAll(imageTags())
}
