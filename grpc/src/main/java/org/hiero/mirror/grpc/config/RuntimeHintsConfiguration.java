// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import java.lang.reflect.Field;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            try {
                Class<?> type = Class.forName(
                        "com.github.benmanes.caffeine.cache.SSSMSA",
                        false,
                        classLoader != null ? classLoader : getClass().getClassLoader());

                Field factory = type.getDeclaredField("FACTORY");
                hints.reflection().registerField(factory);

                // TODO i think this field can be removed
                hints.reflection()
                        .registerType(
                                TypeReference.of("org.hiero.mirror.grpc.repository.CacheHelper"),
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

                hints.reflection()
                        .registerType(
                                TypeReference.of("org.hiero.mirror.grpc.repository.CacheHelper"),
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS);

            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                // no-op;
            }
        }
    }
}
