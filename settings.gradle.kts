// SPDX-License-Identifier: Apache-2.0

plugins { id("com.gradle.develocity") version ("3.17") }

rootProject.name = "hiero-mirror-node"

include(":hedera-mirror-common")

include(":graphql")

include(":grpc")

include(":hedera-mirror-importer")

include(":monitor")

include(":protobuf")

include(":hedera-mirror-rest")

include(":hedera-mirror-rest-java")

include(":hedera-mirror-rest:check-state-proof")

include(":hedera-mirror-rest:monitoring")

include(":hedera-mirror-rosetta")

include(":hedera-mirror-test")

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
