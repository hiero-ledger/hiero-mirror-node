// SPDX-License-Identifier: Apache-2.0

import org.springframework.boot.gradle.tasks.aot.ProcessAot

plugins {
    id("java-conventions")
    id("org.openapi.generator")
}

val openApiPackage = "org.hiero.mirror.rest"

openApiGenerate {
    apiPackage = "${openApiPackage}.api"
    configOptions =
        mapOf(
            "developerEmail" to "mirrornode@hedera.com",
            "developerName" to "Mirror Node Team",
            "developerOrganization" to "Hiero",
            "developerOrganizationUrl" to "https://github.com/hiero-ledger/hiero-mirror-node",
            "interfaceOnly" to "true",
            "licenseName" to "Apache License 2.0",
            "licenseUrl" to "https://www.apache.org/licenses/LICENSE-2.0.txt",
            "openApiNullable" to "false",
            "performBeanValidation" to "true",
            "sourceFolder" to "",
            "supportUrlQuery" to "false",
            "useBeanValidation" to "true",
            "useJakartaEe" to "true",
        )
    generateApiTests = false
    generateModelTests = false
    generatorName = "java"
    inputSpec =
        rootDir.resolve("rest").resolve("api").resolve("v1").resolve("openapi.yml").absolutePath
    invokerPackage = "${openApiPackage}.handler"
    library = "native"
    modelPackage = "${openApiPackage}.model"
    typeMappings = mapOf("Timestamp" to "String", "string+binary" to "String")
}

tasks.withType<JavaCompile>().configureEach { dependsOn("openApiGenerate") }

tasks.withType<ProcessAot>().configureEach { dependsOn("openApiGenerate") }

java.sourceSets["main"].java { srcDir(openApiGenerate.outputDir) }
