// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.METHODS_ONLY;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.UNSAFE_ALLOCATED;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionType;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.util.List;
import org.hiero.mirror.common.util.SpelHelper;
import org.springframework.aot.hint.ExecutableMode;
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
        registerReflectionType(hints, Instant[].class, UNSAFE_ALLOCATED);
        registerReflectionType(hints, SpelHelper.class.getName(), METHODS_ONLY);
    }

    private void registerCache(RuntimeHints hints, String className) {
        final var types = List.of(
                TypeReference.of(Caffeine.class),
                TypeReference.of(AsyncCacheLoader.class),
                TypeReference.of("boolean"));

        hints.reflection()
                .registerType(
                        TypeReference.of("com.github.benmanes.caffeine.cache." + className),
                        b -> b.withConstructor(types, ExecutableMode.INVOKE).withField("FACTORY"));
    }

    private void registerNode(RuntimeHints hints, String className) {
        hints.reflection()
                .registerType(
                        TypeReference.of("com.github.benmanes.caffeine.cache." + className),
                        b -> b.withConstructor(List.of(), ExecutableMode.INVOKE));
    }
}
