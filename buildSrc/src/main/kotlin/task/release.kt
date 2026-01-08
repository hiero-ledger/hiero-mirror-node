// SPDX-License-Identifier: Apache-2.0

package task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.withGroovyBuilder

abstract class Release : DefaultTask() {
    @get:InputDirectory abstract val directory: DirectoryProperty

    @get:Input
    @get:Option(description = "The new version to replace in source files", option = "newVersion")
    abstract val newVersion: Property<String>

    @TaskAction
    fun action() {
        replaceVersion("charts/**/Chart.yaml", "(?<=^(appVersion|version): ).+")
        replaceVersion("docker-compose.yml", "(?<=gcr.io/mirrornode/hedera-mirror-.+:).+")
        replaceVersion("gradle.properties", "(?<=^version=).+")
        replaceVersion(
            "rest/**/package*.json",
            "(?<=\"@hiero-ledger/(check-state-proof|mirror-rest|mirror-monitor)\",\\s{3,7}\"version\": \")[^\"]+",
        )
        replaceVersion("rest/**/openapi.yml", "(?<=^  version: ).+")
        replaceVersion(
            "tools/log-downloader/package*.json",
            "(?<=\"@hiero-ledger/mirror-log-downloader\",\\s{3,7}\"version\": \")[^\"]+",
        )
    }

    fun replaceVersion(files: String, match: String) {
        ant.withGroovyBuilder {
            "replaceregexp"("match" to match, "replace" to newVersion.get(), "flags" to "gm") {
                "fileset"(
                    "dir" to directory.get().asFile.absolutePath,
                    "includes" to files,
                    "excludes" to "**/node_modules/",
                )
            }
        }
    }
}
