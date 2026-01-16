// SPDX-License-Identifier: Apache-2.0

package plugin.go

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

// The Golang plugin that registers the extension and the setup task
class GoPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val go = project.extensions.create<GoExtension>("go")
        go.arch = detectArchitecture()
        go.cacheDir = project.rootDir.resolve(".gradle")
        go.goRoot = go.cacheDir.resolve("go")
        go.goBin = go.goRoot.resolve("bin").resolve("go")
        go.os = detectOs()
        project.tasks.register<GoSetup>("setup")
    }

    fun detectArchitecture(): String {
        val env = System.getProperty("GOARCH", "")

        if (env.isNotBlank()) {
            return env
        } else if (Os.isArch("aarch64")) {
            return "arm64"
        }

        return "amd64"
    }

    fun detectOs(): String {
        val env = System.getProperty("GOOS", "")

        if (env.isNotBlank()) {
            return env
        } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            return "windows"
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            return "darwin"
        }

        return "linux"
    }
}
