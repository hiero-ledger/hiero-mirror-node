// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
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
            // TODO make this change elsewhere
            hints.reflection()
                    .registerType(
                            TypeReference.of("com.github.benmanes.caffeine.cache.SSSMSA"),
                            builder -> builder.withField("FACTORY"));

            hints.reflection()
                    .registerType(
                            TypeReference.of("com.github.benmanes.caffeine.cache.SSR"),
                            builder -> builder.withField("FACTORY"));

            hints.reflection()
                    .registerType(
                            EntityProperties.PersistProperties.class,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);

            hints.reflection()
                    .registerType(
                            TypeReference.of(
                                    "org.hiero.mirror.importer.migration.ContractLogIndexMigration$RecordFileSlice"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);

            hints.reflection()
                    .registerType(
                            TypeReference.of(
                                    "org.hiero.mirror.importer.migration.AbstractTimestampInfoMigration$TimestampInfo"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.hiero.mirror.importer.config.MetricsConfiguration$TableMetrics"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.hiero.mirror.importer.config.MetricsConfiguration$TableAttributes"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);
            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.resource.ResourceManagerImpl"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.resource.ResourceCacheImpl"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.log.JdkLogChute"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Stop"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Define"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Break"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Evaluate"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Parse"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Include"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Foreach"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.If"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Macro"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.directive.Literal"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.parser.StandardParser"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.runtime.ParserPoolImpl"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.util.introspection.UberspectImpl"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection()
                    .registerType(
                            TypeReference.of("org.apache.velocity.util.introspection.TypeConversionHandlerImpl"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection().registerType(java.time.Duration.class, MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.serialization().registerType(EntityId.class);

            hints.resources().registerPattern("org/apache/velocity/runtime/defaults/*");
            hints.resources().registerPattern("db/template/**");

            // TODO verify these are necessary
            hints.resources().registerPattern("db/migration/common/**");
            hints.resources().registerPattern("db/migration/v1/**");
            hints.resources().registerPattern("db/migration/v2/**");
        }
    }
}
