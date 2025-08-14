// SPDX-License-Identifier: Apache-2.0

description = "Hiero Mirror Node gRPC API"

plugins { id("spring-conventions") }

dependencies {
    implementation(project(":common"))
    implementation(project(":protobuf"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.ongres.scram:client")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-core")
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-services")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.projectreactor.addons:reactor-extra")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("net.devh:grpc-spring-boot-starter")
    implementation("org.msgpack:jackson-dataformat-msgpack")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    runtimeOnly(
        group = "io.netty",
        name = "netty-resolver-dns-native-macos",
        classifier = "osx-aarch_64",
    )
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
