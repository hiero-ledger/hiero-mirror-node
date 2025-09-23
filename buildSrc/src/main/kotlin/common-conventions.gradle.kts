// SPDX-License-Identifier: Apache-2.0

plugins { id("org.owasp.dependencycheck") }

repositories { mavenCentral() }

val resources = rootDir.resolve("buildSrc").resolve("src").resolve("main").resolve("resources")

dependencyCheck {
    val env = System.getenv()

    analyzers {
        experimentalEnabled = true
        golangModEnabled = false // Too many vulnerabilities in transitive dependencies currently
        ossIndex {
            password = env.getOrDefault("SONATYPE_OSS_INDEX_TOKEN", "")
            username = env.getOrDefault("SONATYPE_OSS_INDEX_UNAME", "")
        }
    }
    failBuildOnCVSS = 8f
    nvd {
        apiKey = env.getOrDefault("NVD_API_KEY", "")
        datafeedUrl = "https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/"
    }
    suppressionFile = resources.resolve("suppressions.xml").toString()
}

tasks.register<Exec>("uploadCoverage") {
    description = "Uploads coverage report"
    group = "verification"
    var args = ""

    if (pluginManager.hasPlugin("java-conventions")) {
        args = "-l Java -r build/reports/jacoco/test/jacocoTestReport.xml"
    } else if (pluginManager.hasPlugin("javascript-conventions")) {
        args = "-l Javascript -r build/coverage/lcov.info"
    } else if (pluginManager.hasPlugin("go-conventions")) {
        args = "-l Go --force-coverage-parser go -r coverage.txt"
    }

    commandLine(
        listOf(
            "bash",
            "-c",
            "bash <(curl -Ls https://coverage.codacy.com/get.sh) report --partial $args",
        )
    )
    onlyIf { System.getenv().containsKey("CI") && System.getenv("CODACY_PROJECT_TOKEN") != null }
}
