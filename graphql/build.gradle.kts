// SPDX-License-Identifier: Apache-2.0

import com.graphql_java_generator.plugin.conf.CustomScalarDefinition
import com.graphql_java_generator.plugin.conf.PluginMode

description = "Hiero Mirror Node GraphQL"

plugins {
    id("com.graphql-java-generator.graphql-gradle-plugin3")
    id("spring-conventions")
}

dependencies {
    annotationProcessor("org.mapstruct:mapstruct-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.graphql-java-generator:graphql-java-client-runtime") {
        exclude(group = "org.springframework.security")
    }
    implementation(project(":common"))
    implementation("com.graphql-java:graphql-java-extended-scalars")
    implementation("com.graphql-java:graphql-java-extended-validation")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.mapstruct:mapstruct")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

generatePojoConf {
    isAddRelayConnections = false
    isCopyRuntimeSources = true
    isUseJakartaEE9 = true
    javaTypeForIDType = "java.lang.String"
    mode = PluginMode.server
    packageName = "org.hiero.mirror.graphql.viewmodel"
    schemaFilePattern = "**/*.graphqls"
    setCustomScalars(
        arrayOf(
            CustomScalarDefinition(
                "Duration",
                "java.time.Duration",
                "",
                "org.hiero.mirror.graphql.config.GraphQlDuration.INSTANCE",
                "",
            ),
            CustomScalarDefinition("Long", "java.lang.Long", "", "graphql.scalars.GraphQLLong", ""),
            CustomScalarDefinition("Object", "java.lang.Object", "", "graphql.scalars.Object", ""),
            CustomScalarDefinition(
                "Timestamp",
                "java.time.Instant",
                "",
                "org.hiero.mirror.graphql.config.GraphQlTimestamp.INSTANCE",
                "",
            ),
        )
    )
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.generatePojo)
    if (name == "compileJava") {
        options.compilerArgs.addAll(
            listOf(
                "-Amapstruct.defaultComponentModel=jakarta",
                "-Amapstruct.defaultInjectionStrategy=constructor",
                "-Amapstruct.disableBuilders=true",
                "-Amapstruct.unmappedTargetPolicy=IGNORE", // Remove once all Account fields have
                // been mapped
            )
        )
    }
}

java.sourceSets["main"].java { srcDir(tasks.generatePojo) }
