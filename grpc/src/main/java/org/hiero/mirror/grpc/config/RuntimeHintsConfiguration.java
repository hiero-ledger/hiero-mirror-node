// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;

import jakarta.annotation.Nullable;
import lombok.CustomLog;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
@NullMarked
final class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

            // Register @Validated classes where @Valid is used on method parameter
            registerReflectionTypes(hints, TopicMessageFilter.class.getName());
        }
    }
}
