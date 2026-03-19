// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionField;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;

import lombok.CustomLog;
import org.hiero.mirror.common.util.SpelHelper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
@NullMarked
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            final var loader = classLoader != null ? classLoader : getClass().getClassLoader();
            registerReflectionField(hints, loader, "com.github.benmanes.caffeine.cache.SSSMSA", "FACTORY");
            registerReflectionTypes(hints, SpelHelper.class.getName());
        }
    }
}
