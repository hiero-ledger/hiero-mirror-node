// SPDX-License-Identifier: Apache-2.0

import com.github.gradle.node.npm.task.NpmTask
import org.gradle.internal.io.NullOutputStream

plugins {
    id("com.github.node-gradle.node")
    id("common-conventions")
    id("jacoco")
}

node {
    val nodeJsVersion: String by rootProject.extra
    download = true
    version = nodeJsVersion
}

tasks.register("clean") { layout.buildDirectory.asFile.get().deleteRecursively() }

tasks.register<NpmTask>("run") {
    dependsOn(tasks.npmInstall)
    args = listOf("start")
}

val test =
    tasks.register<NpmTask>("test") {
        dependsOn(tasks.npmInstall)
        args = listOf("test")
        execOverrides {
            // Gradle is logging all NPM output to stdout, so this change makes it behave like other
            // tasks and not log
            if (gradle.startParameter.logLevel >= LogLevel.LIFECYCLE) {
                standardOutput = NullOutputStream.INSTANCE
            }
        }
    }

tasks.register("build") { dependsOn(test) }

tasks.dependencyCheckAggregate { dependsOn(tasks.npmInstall) }

tasks.dependencyCheckAnalyze { dependsOn(tasks.npmInstall) }
