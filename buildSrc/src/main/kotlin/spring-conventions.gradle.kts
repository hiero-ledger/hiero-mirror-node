// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.springframework.boot")
}

gitProperties {
    dotGitDirectory = rootDir.resolve(".git")
}

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo()
}

tasks.named("dockerBuild") { dependsOn(tasks.bootJar) }

tasks.register("run") { dependsOn(tasks.bootRun) }

tasks.bootBuildImage {
    // The default builder does not support arm64
    if (System.getProperty("os.arch").lowercase().startsWith("aarch")) {
        builder = "dashaun/builder:tiny"
    }
}
