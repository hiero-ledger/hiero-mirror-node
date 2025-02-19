/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("java-conventions")
    id("org.openapi.generator")
}

val openApiPackage = "com.hedera.mirror.rest"

openApiGenerate {
    apiPackage = "${openApiPackage}.api"
    configOptions =
        mapOf(
            "developerEmail" to "mirrornode@hedera.com",
            "developerName" to "Hedera Mirror Node Team",
            "developerOrganization" to "Hedera Hashgraph",
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
        rootDir
            .resolve("hedera-mirror-rest")
            .resolve("api")
            .resolve("v1")
            .resolve("openapi.yml")
            .absolutePath
    invokerPackage = "${openApiPackage}.handler"
    library = "native"
    modelPackage = "${openApiPackage}.model"
    typeMappings = mapOf("Timestamp" to "String", "string+binary" to "String")
}

tasks.withType<JavaCompile>().configureEach { dependsOn("openApiGenerate") }

java.sourceSets["main"].java { srcDir(openApiGenerate.outputDir) }
