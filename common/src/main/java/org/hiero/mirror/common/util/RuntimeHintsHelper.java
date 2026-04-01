// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.lang.annotation.Annotation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@NullMarked
public final class RuntimeHintsHelper {

    private static final MemberCategory[] DEFAULT_CATEGORIES = {
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
        MemberCategory.INVOKE_DECLARED_METHODS,
        MemberCategory.ACCESS_DECLARED_FIELDS,
    };
    public static final MemberCategory[] NONE = {};
    public static final MemberCategory[] UNSAFE_ALLOCATED = {MemberCategory.UNSAFE_ALLOCATED};
    public static final MemberCategory[] METHODS_ONLY = {MemberCategory.INVOKE_DECLARED_METHODS};
    public static final MemberCategory[] CONSTRUCTORS_AND_METHODS = {
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS
    };
    public static final MemberCategory[] FIELDS_AND_METHODS = {
        MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS
    };

    public static void registerReflectionTypes(RuntimeHints hints, String... classNames) {
        registerReflectionTypes(hints, DEFAULT_CATEGORIES, classNames);
    }

    public static void registerReflectionTypes(
            RuntimeHints hints, MemberCategory[] memberCategories, String... classNames) {
        for (final var className : classNames) {
            registerReflectionType(hints, className, memberCategories);
        }
    }

    public static void registerReflectionTypes(
            RuntimeHints hints, MemberCategory[] memberCategories, Class<?>... types) {
        for (final var type : types) {
            registerReflectionType(hints, type, memberCategories);
        }
    }

    public static void registerReflectionType(RuntimeHints hints, Class<?> type, MemberCategory... memberCategories) {
        hints.reflection().registerType(TypeReference.of(type), memberCategories);
    }

    public static void registerReflectionType(RuntimeHints hints, String className) {
        registerType(hints, className, DEFAULT_CATEGORIES);
    }

    public static void registerReflectionType(
            RuntimeHints hints, String className, MemberCategory... memberCategories) {
        registerType(hints, className, memberCategories);
    }

    public static void registerAnnotatedPackage(
            RuntimeHints hints, ClassLoader loader, String basePackage, Class<? extends Annotation> annotationType) {
        registerAnnotatedPackage(hints, loader, basePackage, annotationType, DEFAULT_CATEGORIES);
    }

    public static void registerAnnotatedPackage(
            RuntimeHints hints,
            ClassLoader loader,
            String basePackage,
            Class<? extends Annotation> annotationType,
            MemberCategory... memberCategories) {
        registerPackageMatching(hints, loader, basePackage, new AnnotationTypeFilter(annotationType), memberCategories);
    }

    public static void registerPackage(RuntimeHints hints, ClassLoader loader, String basePackage) {
        registerPackage(hints, loader, basePackage, DEFAULT_CATEGORIES);
    }

    public static void registerPackage(
            RuntimeHints hints, ClassLoader loader, String basePackage, MemberCategory... memberCategories) {
        registerPackageMatching(
                hints,
                loader,
                basePackage,
                (metadataReader, metadataReaderFactory) ->
                        metadataReader.getClassMetadata().getClassName().startsWith(basePackage),
                memberCategories);
    }

    public static void registerResourcePatterns(RuntimeHints hints, String... patterns) {
        for (final var pattern : patterns) {
            hints.resources().registerPattern(pattern);
        }
    }

    public static void registerSerialization(RuntimeHints hints, ClassLoader loader, String... classNames) {
        for (final var className : classNames) {
            try {
                final var clazz = Class.forName(className, false, loader);
                hints.reflection().registerJavaSerialization(clazz);
            } catch (ClassNotFoundException e) {
                // no-op
            }
        }
    }

    private static void registerType(RuntimeHints hints, String className, MemberCategory... memberCategories) {
        final var type = TypeReference.of(className);
        hints.reflection().registerType(type, memberCategories);
    }

    private static void registerPackageMatching(
            RuntimeHints hints,
            ClassLoader loader,
            String basePackage,
            TypeFilter includeFilter,
            MemberCategory... memberCategories) {
        final var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(includeFilter);

        for (final var candidate : scanner.findCandidateComponents(basePackage)) {
            final var className = candidate.getBeanClassName();
            if (className == null) {
                continue;
            }

            registerLoadedClass(hints, loader, className, memberCategories);
        }
    }

    private static void registerLoadedClass(
            RuntimeHints hints, ClassLoader loader, String className, MemberCategory... memberCategories) {
        try {
            final var type = Class.forName(className, false, loader);
            hints.reflection().registerType(type, memberCategories);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to register runtime hints for " + className, e);
        }
    }
}
