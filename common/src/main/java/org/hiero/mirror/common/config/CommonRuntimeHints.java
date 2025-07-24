// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public class CommonRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Caffeine loads generated cache implementations via reflection
        registerCache(hints, "SSMSA");
        registerCache(hints, "SSR");
        registerCache(hints, "SSSMA");
        registerCache(hints, "SSSMSA");
        registerCache(hints, "SSSW");
        registerNode(hints, "PSAMS");
        registerNode(hints, "PSR");

        // For ReconciliationJob.timestampStart use as an ID in Hibernate
        hints.reflection().registerType(TypeReference.of(Instant[].class), MemberCategory.UNSAFE_ALLOCATED);
    }

    private void registerCache(RuntimeHints hints, String className) {
        final var types = List.of(
                TypeReference.of(Caffeine.class),
                TypeReference.of(AsyncCacheLoader.class),
                TypeReference.of("boolean"));
        hints.reflection()
                .registerType(
                        TypeReference.of("com.github.benmanes.caffeine.cache." + className),
                        b -> b.withConstructor(types, ExecutableMode.INVOKE));
    }

    private void registerNode(RuntimeHints hints, String className) {
        hints.reflection()
                .registerType(
                        TypeReference.of("com.github.benmanes.caffeine.cache." + className),
                        b -> b.withConstructor(List.of(), ExecutableMode.INVOKE));
    }
}
