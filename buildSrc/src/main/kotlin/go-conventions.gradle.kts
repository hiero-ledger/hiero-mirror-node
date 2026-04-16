// SPDX-License-Identifier: Apache-2.0

import org.gradle.internal.io.NullOutputStream
import plugin.go.Go
import plugin.go.GoExtension
import plugin.go.GoPlugin

plugins {
    id("common-conventions")
    id("jacoco")
}

apply<GoPlugin>()

val go = project.extensions.getByName<GoExtension>("go")

val goBuild =
    tasks.register<Go>("goBuild") {
        val binary = layout.buildDirectory.asFile.get().resolve(layout.projectDirectory.asFile.name)
        val ldFlags = "-w -s -X main.Version=${project.version}"
        environment["CGO_ENABLED"] = "true"
        args("build", "-ldflags", ldFlags, "-o", binary)
        dependsOn("test")
    }

val goClean =
    tasks.register<Go>("goClean") {
        args("clean")
        layout.buildDirectory.asFile.get().deleteRecursively()
        projectDir.resolve("coverage.txt").delete()
    }

val gitDiff =
    tasks.register<Exec>("gitDiff") {
        // Fail the build if go fix actually modified tracked files
        commandLine("git", "diff", "--exit-code", layout.projectDirectory)
    }

tasks.register<Go>("fix") {
    args("fix", "./...")
    // go1.26.0 bug causes exit=1 even when no changes (prints "files updated")
    // https://github.com/golang/go/issues/77482
    isIgnoreExitValue = true
    finalizedBy(gitDiff)
}

tasks.register<Go>("fmt") {
    args("fmt", "./...")
    dependsOn("generate")
}

tasks.register<Go>("generate") {
    args("generate", "./...")
    dependsOn("fix")
}

tasks.register<Exec>("run") {
    group = "application"
    commandLine(layout.buildDirectory.asFile.get().resolve(layout.projectDirectory.asFile.name))
    dependsOn(goBuild)
}

tasks.register<Go>("test") {
    args(
        "test",
        "-coverpkg=${go.pkg}",
        "-coverprofile=coverage.txt",
        "-covermode=atomic",
        "-race",
        "-v",
        go.pkg,
    )
    dependsOn("fix")
    val disableLogging = gradle.startParameter.logLevel >= LogLevel.LIFECYCLE
    doFirst {
        if (disableLogging) {
            standardOutput = NullOutputStream.INSTANCE
        }
    }
}

tasks.register("build") { dependsOn(goBuild) }

tasks.register("clean") { dependsOn(goClean) }

// Ensure go binary is installed before running dependency check
listOf(tasks.dependencyCheckAggregate, tasks.dependencyCheckAnalyze).forEach {
    it.configure {
        dependsOn("setup")
        doFirst { dependencyCheck { analyzers { pathToGo = go.goBin.toString() } } }
    }
}

// Ensure the Gradle-installed Go is on PATH for nested tools that exec "go"
tasks.withType<Go>().configureEach {
    val goBinDir = go.goBin.parentFile.absolutePath
    environment("PATH", "${goBinDir}:${System.getenv("PATH")}")
}
