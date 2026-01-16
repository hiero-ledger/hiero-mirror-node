// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hiero.mirror.common.CommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.util.FileSystemUtils;

@Import(CommonConfiguration.class)
@SpringBootApplication
public class Web3Application {

    public static void main(String[] args) {
        cleanup();
        SpringApplication.run(Web3Application.class, args);
    }

    private static void cleanup() {
        final var tmpDir = Path.of(System.getProperty("java.io.tmpdir", "/tmp/web3"));

        try {
            FileSystemUtils.deleteRecursively(tmpDir);
            Files.createDirectories(tmpDir);
        } catch (IOException ex) {
            System.err.println("Could not delete tmp directory %s: %s".formatted(tmpDir, ex.getMessage()));
        }
    }
}
