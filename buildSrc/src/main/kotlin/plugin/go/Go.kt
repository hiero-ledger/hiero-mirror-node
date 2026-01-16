// SPDX-License-Identifier: Apache-2.0

package plugin.go

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.getByName

// Template task to execute the go CLI
abstract class Go : Exec() {

    @Internal val go = project.extensions.getByName<GoExtension>("go")

    init {
        dependsOn("setup")
    }

    override fun exec() {
        logger.info("Executing go ${args}")
        executable(go.goBin.absolutePath)
        super.exec()
    }
}
