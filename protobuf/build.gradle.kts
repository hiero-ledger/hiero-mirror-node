// SPDX-License-Identifier: Apache-2.0

import com.google.protobuf.gradle.id

description = "Hiero Mirror Node Protobuf"

plugins {
    id("com.google.protobuf")
    id("java-conventions")
}

dependencies {
    api("com.hedera.hashgraph:hedera-protobuf-java-api") { isTransitive = false }
    api("com.salesforce.servicelibs:reactor-grpc-stub")
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
    api("io.projectreactor:reactor-core")
}

protobuf {
    val protobufVersion: String by rootProject.extra
    val reactorGrpcVersion: String by rootProject.extra

    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java" }
        id("reactor") { artifact = "com.salesforce.servicelibs:reactor-grpc:$reactorGrpcVersion" }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
                id("reactor")
            }
        }
    }
}
