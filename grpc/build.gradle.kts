// SPDX-License-Identifier: Apache-2.0

description = "Hiero Mirror Node gRPC API"

plugins { id("spring-conventions") }

dependencies {
    implementation(project(":common")) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
        exclude(group = "org.hibernate.orm")
    }
    implementation(project(":protobuf"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.ongres.scram:client")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-services")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.projectreactor.addons:reactor-extra")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.hibernate.orm:hibernate-core")
    implementation("org.msgpack:jackson-dataformat-msgpack")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.grpc:spring-grpc-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
