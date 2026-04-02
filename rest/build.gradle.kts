// SPDX-License-Identifier: Apache-2.0

import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.Exec

description = "Mirror Node REST API"

plugins {
    id("docker-conventions")
    id("javascript-conventions")
}

// Works around an implicit task dependency due to an output file of monitor dockerBuild present in
// the input file list of rest dockerBuild due to it being in a sub-folder.
tasks.dockerBuild { dependsOn(":rest:monitoring:dockerBuild") }

// Regenerates rest/gen/** from rest/proto via buf generate using @bufbuild/protoc-gen-es.
tasks.register<Exec>("generateRestProtoJs") {
    group = "build"
    description =
        "Regenerates rest/gen/** from rest/proto via buf generate (@bufbuild/protoc-gen-es)"
    dependsOn(tasks.npmInstall)
    workingDir = layout.projectDirectory.asFile
    val bufExecutable = layout.projectDirectory.dir("node_modules/.bin").file("buf").asFile
    commandLine(bufExecutable.absolutePath, "generate")
    inputs.dir(layout.projectDirectory.dir("proto"))
    inputs.files(
        layout.projectDirectory.file("buf.yaml"),
        layout.projectDirectory.file("buf.gen.yaml"),
        layout.projectDirectory.file("package.json"),
        layout.projectDirectory.file("package-lock.json"),
    )
    outputs.dir(layout.projectDirectory.dir("gen"))
}

tasks.register<NpmTask>("testRestJava") {
    val specPaths = listOf("network/nodes")
    val testFiles = listOf("network.spec.test.js")

    dependsOn(":rest-java:dockerBuild")
    onlyIf { specPaths.isNotEmpty() && testFiles.isNotEmpty() }

    // Configure spec test(s) to run
    val includeRegex = specPaths.joinToString("|")
    environment.put("REST_JAVA_INCLUDE", includeRegex)

    val testPathPattern = testFiles.joinToString("|")
    args = listOf("test", "--testPathPattern", testPathPattern)
}
