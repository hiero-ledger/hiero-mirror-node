// SPDX-License-Identifier: Apache-2.0

import com.google.protobuf.gradle.id

description = "Hiero Mirror Node Protobuf"

plugins {
    id("com.google.protobuf")
    id("java-conventions")
}

dependencies {
    api("com.hedera.hashgraph:hedera-protobuf-java-api") { isTransitive = false }
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
}

protobuf {
    val grpcVersion = dependencyManagement.importedProperties["grpc-java.version"] as String
    val protobufVersion = dependencyManagement.importedProperties["protobuf-java.version"] as String

    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" } }
    generateProtoTasks { ofSourceSet("main").forEach { it.plugins { id("grpc") } } }
}
