// SPDX-License-Identifier: Apache-2.0

import com.github.gradle.node.npm.task.NpmTask

description = "Mirror Node REST API"

plugins {
    id("docker-conventions")
    id("javascript-conventions")
}

// Works around an implicit task dependency due to an output file of monitor dockerBuild present in
// the input file list of rest dockerBuild due to it being in a sub-folder.
tasks.dockerBuild { dependsOn(":rest:monitoring:dockerBuild") }

tasks.register<NpmTask>("testRestJava") {
    val specPaths = listOf("network/fees", "network/supply")
    val testFiles = listOf("network.spec.test.js")

    dependsOn(":rest-java:dockerBuild")
    onlyIf { specPaths.isNotEmpty() && testFiles.isNotEmpty() }

    // Configure spec test(s) to run
    val includeRegex = specPaths.joinToString("|")
    environment.put("REST_JAVA_INCLUDE", includeRegex)

    val testPathPattern = testFiles.joinToString("|")
    args = listOf("test", "--testPathPattern", testPathPattern)
}
