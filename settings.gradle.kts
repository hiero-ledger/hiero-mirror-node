// SPDX-License-Identifier: Apache-2.0

plugins { id("com.gradle.develocity") version ("3.17") }

rootProject.name = "hiero-mirror-node"

include(":common")

include(":graphql")

include(":grpc")

include(":importer")

include(":monitor")

include(":protobuf")

include(":rest")

include(":hedera-mirror-rest-java")

include(":rest:check-state-proof")

include(":rest:monitoring")

include(":rosetta")

include(":test")

include(":hedera-mirror-web3")

shortenProjectName(rootProject)

// Shorten project name to remove verbose "hedera-mirror-" prefix
fun shortenProjectName(project: ProjectDescriptor) {
    if (project != rootProject) {
        project.name = project.name.removePrefix("hedera-mirror-")
    }
    project.children.forEach(this::shortenProjectName)
}

develocity {
    buildScan {
        publishing.onlyIf { System.getenv().containsKey("CI") }
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        tag("CI")
    }
}

// Temporarily workaround sonarqube depends on compile task warning
System.setProperty("sonar.gradle.skipCompile", "true")
