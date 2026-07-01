// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.CONSTRUCTORS_AND_METHODS;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.DEFAULT_CATEGORIES;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackageMatching;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerResourcePatterns;

import java.time.Duration;
import org.apache.velocity.runtime.ParserPoolImpl;
import org.apache.velocity.runtime.directive.Foreach;
import org.apache.velocity.runtime.parser.StandardParser;
import org.apache.velocity.runtime.resource.ResourceCacheImpl;
import org.apache.velocity.runtime.resource.ResourceManagerImpl;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.introspection.TypeConversionHandlerImpl;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
@NullMarked
final class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            final var loader = classLoader != null ? classLoader : getClass().getClassLoader();

            registerReflectionTypes(hints, EntityProperties.PersistProperties.class.getName());
            registerMigrationClasses(hints, classLoader);
            registerReflectionTypes(
                    hints,
                    CONSTRUCTORS_AND_METHODS,
                    BlockProperties.class.getName(),
                    "org.hiero.mirror.importer.config.MetricsConfiguration$TableMetrics",
                    "org.hiero.mirror.importer.config.MetricsConfiguration$TableAttributes");

            registerVelocityHints(hints, loader);

            registerResourcePatterns(
                    hints,
                    "addressbook/**",
                    "accountInfo.txt.gz",
                    "accountInfoContracts.txt",
                    "com/hedera/nativelib/wraps/**",
                    "com/hedera/nativelib/hints/**",
                    "db/migration/common/**",
                    "db/migration/v1/**",
                    "db/migration/v2/**",
                    "errata/**",
                    // zstd native libraries
                    "linux/aarch64/libzstd-jni-*.so",
                    "linux/amd64/libzstd-jni-*.so",
                    "win/aarch64/libzstd-jni-*.dll",
                    "win/amd64/libzstd-jni-*.dll");
        }

        // Register migration member classes created reflectively via RowMapper
        private void registerMigrationClasses(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            TypeFilter memberClassFilter = (r, _) -> {
                try {
                    final var clazz = ClassUtils.forName(r.getClassMetadata().getClassName(), classLoader);
                    return clazz.isMemberClass() && !clazz.isEnum() && !clazz.isInterface();
                } catch (Exception e) {
                    return false;
                }
            };
            registerPackageMatching(
                    hints, classLoader, "org.hiero.mirror.importer.migration", memberClassFilter, DEFAULT_CATEGORIES);
        }

        private void registerVelocityHints(RuntimeHints hints, ClassLoader loader) {
            registerPackage(hints, loader, Foreach.class.getPackageName(), CONSTRUCTORS_AND_METHODS);

            registerReflectionTypes(
                    hints,
                    CONSTRUCTORS_AND_METHODS,
                    ResourceManagerImpl.class,
                    ResourceCacheImpl.class,
                    ClasspathResourceLoader.class,
                    StandardParser.class,
                    ParserPoolImpl.class,
                    UberspectImpl.class,
                    TypeConversionHandlerImpl.class,
                    Duration.class);

            registerResourcePatterns(hints, "org/apache/velocity/runtime/defaults/*", "db/template/**");
        }
    }
}
