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

include(":rest-java")

include(":rest:check-state-proof")

include(":rest:monitoring")

include(":rosetta")

include(":test")

include(":web3")

develocity {
    buildScan {
        publishing.onlyIf { System.getenv().containsKey("CI") }
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        tag("CI")
    }
}
