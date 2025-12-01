// SPDX-License-Identifier: Apache-2.0

package plugin.go

import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByName

// Downloads and decompresses the Go artifacts
open class GoSetup : DefaultTask() {

    @Internal val go = project.extensions.getByName<GoExtension>("go")

    @TaskAction
    fun prepare() {
        go.goRoot.mkdirs()
        val url =
            URI.create(
                    "https://go.dev/dl/go${go.version}.${go.os}-${go.arch}.tar.gz"
                )
                .toURL()
        val filename = Paths.get(url.path).fileName
        val targetFile = go.cacheDir.toPath().resolve(filename)

        if (!targetFile.toFile().exists()) {
            go.goRoot.deleteRecursively()
            url.openStream().use {
                logger.warn("Downloading: ${url}")
                Files.copy(it, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        if (!go.goBin.exists()) {
            decompressTgz(targetFile.toFile(), go.cacheDir)
        }
    }

    fun decompressTgz(source: File, destDir: File) {
        TarArchiveInputStream(GZIPInputStream(FileInputStream(source)), "UTF-8").use {
            destDir.mkdirs()
            var entry = it.nextEntry

            while (entry != null) {
                val file = destDir.resolve(entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!file.setExecutable(true, true)) {
                        throw IllegalStateException("Unable to set execute bit on file " + file)
                    }
                }
                entry = it.nextEntry
            }
        }
    }
}
