// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import lombok.CustomLog;
import org.hiero.mirror.rest.model.Error;
import org.hiero.mirror.restjava.config.RuntimeHintsConfiguration.CustomRuntimeHints;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

@Configuration
@CustomLog
@ImportRuntimeHints(CustomRuntimeHints.class)
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            registerOpenApiModels(hints);
            registerRequestParameters(hints);
        }

        /**
         * Jackson uses reflection to instantiate the OpenAPI generated response models.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerOpenApiModels(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            scanner.findCandidateComponents(Error.class.getPackageName()).forEach(b -> hints.reflection()
                    .registerType(
                            TypeReference.of(b.getBeanClassName()),
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_METHODS));
        }

        /**
         * RequestParameterArgumentResolver uses reflection to access fields and annotations on DTOs annotated with
         * {@link RequestParameter}.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerRequestParameters(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RequestParameter.class));
            scanner.findCandidateComponents("org.hiero.mirror.restjava.dto").forEach(b -> hints.reflection()
                    .registerType(
                            TypeReference.of(b.getBeanClassName()),
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_METHODS));
        }
    }
}
