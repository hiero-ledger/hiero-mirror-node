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
    dependsOn(":rest-java:dockerBuild")
    // Configure regex to match the parent folder of specs
    environment.put("REST_JAVA_INCLUDE", "^.*(none)$")

    // Configure spec test(s) to run
    args = listOf("test", "--testNamePattern", "^.*(none.spec.test.js)$")
}
