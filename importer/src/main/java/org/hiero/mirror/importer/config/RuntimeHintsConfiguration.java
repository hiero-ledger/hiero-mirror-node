// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.CONSTRUCTORS_AND_METHODS;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionField;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerResourcePatterns;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerSerialization;

import java.time.Duration;
import lombok.CustomLog;
import org.apache.velocity.runtime.ParserPoolImpl;
import org.apache.velocity.runtime.directive.Foreach;
import org.apache.velocity.runtime.parser.StandardParser;
import org.apache.velocity.runtime.resource.ResourceCacheImpl;
import org.apache.velocity.runtime.resource.ResourceManagerImpl;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.introspection.TypeConversionHandlerImpl;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
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
            registerReflectionField(hints, loader, "com.github.benmanes.caffeine.cache.SSR", "FACTORY");

            registerReflectionTypes(
                    hints,
                    EntityProperties.PersistProperties.class.getName(),
                    "org.hiero.mirror.importer.migration.ContractLogIndexMigration$RecordFileSlice",
                    "org.hiero.mirror.importer.migration.AbstractTimestampInfoMigration$TimestampInfo",
                    "org.hiero.mirror.importer.config.MetricsConfiguration$TableMetrics",
                    "org.hiero.mirror.importer.config.MetricsConfiguration$TableAttributes");

            registerPackage(hints, loader, Foreach.class.getPackageName(), CONSTRUCTORS_AND_METHODS);

            registerReflectionTypes(
                    hints,
                    CONSTRUCTORS_AND_METHODS,
                    ResourceManagerImpl.class.getName(),
                    ResourceCacheImpl.class.getName(),
                    ClasspathResourceLoader.class.getName(),
                    StandardParser.class.getName(),
                    ParserPoolImpl.class.getName(),
                    UberspectImpl.class.getName(),
                    TypeConversionHandlerImpl.class.getName(),
                    Duration.class.getName());

            registerSerialization(hints, EntityId.class.getName());

            registerResourcePatterns(
                    hints,
                    "org/apache/velocity/runtime/defaults/*",
                    "db/template/**",
                    "db/migration/common/**",
                    "db/migration/v1/**",
                    "db/migration/v2/**");
        }
    }
}
