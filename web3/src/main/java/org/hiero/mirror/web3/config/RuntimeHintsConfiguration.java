// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import com.swirlds.config.api.ConfigData;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AnnotationTypeFilter;

@Configuration
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
class RuntimeHintsConfiguration {
    private static final String BASE_NODE_CONFIG_PACKAGE = "com.hedera.node.config.data";

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            ClassLoader loader = classLoader != null ? classLoader : getClass().getClassLoader();

            registerCaffeineHints(hints, loader);

            registerAnnotatedPackage(
                    hints,
                    loader,
                    BASE_NODE_CONFIG_PACKAGE,
                    ConfigData.class,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.resources().registerPattern("semantic-version.properties");
            hints.resources().registerPattern("*.properties");
            hints.resources().registerPattern("**/semantic-version.properties");

            registerJnaAndReflectionType(
                    hints,
                    "com.goterl.lazysodium.Sodium",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerJnaAndReflectionType(
                    hints,
                    "com.goterl.lazysodium.SodiumJava",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerJnaAndReflectionType(hints, "com.sun.jna.Callback");
            registerJnaAndReflectionType(hints, "com.sun.jna.CallbackReference");
            registerJnaAndReflectionType(hints, "com.sun.jna.CallbackReference$AttachOptions");
            registerJnaAndReflectionType(hints, "com.sun.jna.FromNativeConverter");
            registerJnaAndReflectionType(hints, "com.sun.jna.IntegerType");
            registerJnaAndReflectionType(hints, "com.sun.jna.JNIEnv");
            registerJnaAndReflectionType(hints, "com.sun.jna.Native");
            registerJnaAndReflectionType(hints, "com.sun.jna.Native$ffi_callback");
            registerJnaAndReflectionType(hints, "com.sun.jna.NativeMapped");
            registerJnaAndReflectionType(hints, "com.sun.jna.Pointer");
            registerJnaAndReflectionType(hints, "com.sun.jna.PointerType");
            registerJnaAndReflectionType(hints, "com.sun.jna.Structure");
            registerJnaAndReflectionType(hints, "com.sun.jna.Structure$ByValue");
            registerJnaAndReflectionType(hints, "com.sun.jna.Structure$FFIType$FFITypes");
            registerJnaAndReflectionType(hints, "com.sun.jna.WString");

            registerReflectionType(
                    hints,
                    "com.sun.jna.Structure$FFIType",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.Structure$FFIType$size_t",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.ptr.IntByReference",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.NativeLong",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "sun.security.provider.NativePRNG",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.Native",
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.NativeLibrary",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.Pointer",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.sun.jna.NativeLong",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerReflectionType(
                    hints,
                    "com.goterl.resourceloader.SharedLibraryLoader",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            registerReflectionType(
                    hints,
                    "com.goterl.lazysodium.utils.LibraryLoader",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS);

            registerReflectionType(hints, "kotlin.Metadata");
            registerReflectionType(hints, "kotlin.jvm.JvmInline");
            registerReflectionType(hints, "kotlinx.serialization.Serializable");

            registerPackage(
                    hints,
                    loader,
                    "com.hedera.node.app.hapi.utils.sysfiles.domain.throttling",
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);

            registerResourcePatterns(
                    hints,
                    "semantic-version.properties", // TODO find least required of these properties. Should also be able
                    // to remove the ar664 lines etc as we cant use libsodium from the
                    // classpath
                    "*.properties",
                    "**/semantic-version.properties",
                    "com/hedera/nativelib/hints/**",
                    "com/hedera/nativelib/wraps/**",
                    "arm64/**",
                    "armv6/**",
                    "linux/**",
                    "linux64/**",
                    "mac/**",
                    "mac_arm/**",
                    "windows/**",
                    "windows64/**",
                    "genesis/**");
        }

        private void registerCaffeineHints(RuntimeHints hints, ClassLoader loader) {
            try {
                Class<?> type = Class.forName("com.github.benmanes.caffeine.cache.SSSMSA", false, loader);
                Field factory = type.getDeclaredField("FACTORY");
                hints.reflection().registerField(factory);
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                // no-op
            }
        }

        private void registerJnaAndReflectionType(
                RuntimeHints hints, String className, MemberCategory... memberCategories) {
            TypeReference type = TypeReference.of(className);

            hints.jni().registerType(type, builder -> builder.withMembers(memberCategories));
            hints.reflection().registerType(type, memberCategories);
        }

        private void registerReflectionType(RuntimeHints hints, String className, MemberCategory... memberCategories) {
            hints.reflection().registerType(TypeReference.of(className), memberCategories);
        }

        private void registerAnnotatedPackage(
                RuntimeHints hints,
                ClassLoader loader,
                String basePackage,
                Class<? extends Annotation> annotationType,
                MemberCategory... memberCategories) {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));

            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                if (className == null) {
                    continue;
                }

                try {
                    Class<?> type = Class.forName(className, false, loader);
                    hints.reflection().registerType(type, memberCategories);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Failed to register runtime hints for " + className, e);
                }
            }
        }

        private void registerResourcePatterns(RuntimeHints hints, String... patterns) {
            for (String pattern : patterns) {
                hints.resources().registerPattern(pattern);
            }
        }

        private void registerPackage(
                RuntimeHints hints, ClassLoader loader, String basePackage, MemberCategory... memberCategories) {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((metadataReader, metadataReaderFactory) ->
                    metadataReader.getClassMetadata().getClassName().startsWith(basePackage));

            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                if (className == null) {
                    continue;
                }

                try {
                    Class<?> type = Class.forName(className, false, loader);
                    hints.reflection().registerType(type, memberCategories);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Failed to register runtime hints for " + className, e);
                }
            }
        }
    }
}
