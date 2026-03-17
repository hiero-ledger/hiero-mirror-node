// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.CustomLog;

@CustomLog
public class NativeLibSodiumLoader {
    private static volatile Path extractedLibraryPath;
    private static volatile boolean loaded;

    private NativeLibSodiumLoader() {}

    public static synchronized void load() {
        log.info("Loading lib sodium ");
        if (loaded) {
            return;
        }

        Path libraryPath = extract();
        System.load(libraryPath.toAbsolutePath().toString());
        loaded = true;
    }

    public static synchronized Path extract() {
        if (extractedLibraryPath != null) {
            return extractedLibraryPath;
        }

        String resourcePath = detectResourcePath();
        String suffix = detectSuffix(resourcePath);

        try (InputStream inputStream = NativeLibSodiumLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Bundled libsodium resource not found: " + resourcePath);
            }

            Path tempFile = Files.createTempFile("libsodium-", suffix);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();

            extractedLibraryPath = tempFile;
            return extractedLibraryPath;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract bundled libsodium from " + resourcePath, e);
        }
    }

    private static String detectResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "/mac_arm/libsodium.dylib";
            }
            return "/mac/libsodium.dylib";
        }

        if (os.contains("win")) {
            if (arch.contains("64")) {
                return "/windows64/libsodium.dll";
            }
            return "/windows/libsodium.dll";
        }

        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "/arm64/libsodium.so";
        }
        if (arch.contains("arm")) {
            return "/armv6/libsodium.so";
        }
        if (arch.contains("64")) {
            return "/linux64/libsodium.so";
        }
        return "/linux/libsodium.so";
    }

    private static String detectSuffix(String resourcePath) {
        if (resourcePath.endsWith(".dylib")) {
            return ".dylib";
        }
        if (resourcePath.endsWith(".dll")) {
            return ".dll";
        }
        return ".so";
    }
}
