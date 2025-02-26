// SPDX-License-Identifier: Apache-2.0

plugins { id("org.owasp.dependencycheck") }

repositories { mavenCentral() }

val resources = rootDir.resolve("buildSrc").resolve("src").resolve("main").resolve("resources")

dependencyCheck {
    if (System.getenv().containsKey("NVD_API_KEY")) {
        nvd.apiKey = System.getenv("NVD_API_KEY")
    }

    failBuildOnCVSS = 8f
    suppressionFile = resources.resolve("suppressions.xml").toString()
    analyzers {
        experimentalEnabled = true
        golangModEnabled = false // Too many vulnerabilities in transitive dependencies currently
    }
    nvd { datafeedUrl = "https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/" }
}
