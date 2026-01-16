// SPDX-License-Identifier: Apache-2.0

plugins { id("io.snyk.gradle.plugin.snykplugin") }

abstract class SnykCodeTask : io.snyk.gradle.plugin.SnykTask() {

    @TaskAction
    fun doSnykTest() {
        log.debug("Snyk Code Test Task")
        authentication()
        val output: io.snyk.gradle.plugin.Runner.Result = runSnykCommand("code test")
        log.lifecycle(output.output)
        if (output.exitcode > 0) {
            throw GradleException("Snyk Code Test failed")
        }
    }
}

tasks.register<SnykCodeTask>("snyk-code") {
    dependsOn("snyk-check-binary")
    snyk {
        setArguments(
            "--all-sub-projects --json-file-output=build/reports/snyk-code.json --org=hiero-mirror-node"
        )
        setSeverity("high")
    }
}

tasks.`snyk-monitor` {
    doFirst { snyk { setArguments("--all-sub-projects --org=hiero-mirror-node") } }
}

tasks.`snyk-test` {
    snyk {
        setArguments(
            "--all-sub-projects --json-file-output=build/reports/snyk-test.json --org=hiero-mirror-node"
        )
        setSeverity("high")
    }
}
