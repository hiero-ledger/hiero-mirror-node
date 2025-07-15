package org.hiero.mirror.importer.blockNode;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class Tests extends BaseClass {

//    @DynamicPropertySource
//    static void overrideSpringProperties(DynamicPropertyRegistry registry) {
//        // PostgreSQL container config
//        registry.add("spring.datasource.url", () -> dbContainer.getJdbcUrl());
//        registry.add("spring.datasource.username", () -> dbContainer.getUsername());
//        registry.add("spring.datasource.password", () -> dbContainer.getPassword());
//
//        // Block Node gRPC endpoint (used by importer)
//        registry.add("hedera.importer.blockNode.grpc.host", () -> blockNodeContainer.getHost());
//        registry.add("hedera"
//                + ".importer.blockNode.grpc.port", () -> blockNodeContainer.getMappedPort(blockNodePort));
//    }

    @Test
    @DisplayName("Test")
    void test() throws IOException, InterruptedException {
        // 1. Get actual container addresses
        var dbUrl = dbContainer.getJdbcUrl();
        var dbUser = dbContainer.getUsername();
        var dbPass = dbContainer.getPassword();

        var grpcHost = blockNodeContainer.getHost();
        var grpcPort = blockNodeContainer.getMappedPort(blockNodePort);

        System.out.println("DB running at: " + dbUrl);
        System.out.println("Block Node gRPC: " + blockNodeContainer.getHost() + ":" + blockNodeContainer.getMappedPort(blockNodePort));
//        var importerContainer = createImporterContainer();
//        importerContainer.start();
//        ProcessBuilder pb = new ProcessBuilder("./gradlew", "importer:bootRun");
//        // Inherit console output so you can see logs
//        pb.inheritIO();
//
//        // Start the process
//        Process process = pb.start();
//
//        // Wait a few seconds for the app to initialize
//        TimeUnit.SECONDS.sleep(15);

        System.out.println("🧪 Running test with Postgres: " + dbContainer.getJdbcUrl());
        System.out.println("🧪 Block Node gRPC endpoint: " + blockNodeContainer.getHost() + ":" + blockNodeContainer.getMappedPort(blockNodePort));
        System.out.println("TEST");

        // Finally stop the importer (clean shutdown)
//        process.destroy();
    }
    }