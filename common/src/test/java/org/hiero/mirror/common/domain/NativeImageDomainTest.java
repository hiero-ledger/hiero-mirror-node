// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

final class NativeImageDomainTest {

    private static final String DOMAIN_PACKAGE = "org.hiero.mirror.common.domain";

    @Test
    void entityIdFieldsHaveEntityIdConverter() throws Exception {
        final var failures = new ArrayList<String>();

        for (final var entityClass : findEntityClasses()) {
            for (final var field : getAllFields(entityClass)) {
                if (!EntityId.class.equals(field.getType()) || shouldSkip(field)) {
                    continue;
                }

                final var convert = field.getAnnotation(Convert.class);
                if (convert == null || !EntityIdConverter.class.equals(convert.converter())) {
                    failures.add("%s.%s must be annotated with @Convert(converter = EntityIdConverter.class)"
                            .formatted(field.getDeclaringClass().getSimpleName(), field.getName()));
                }
            }
        }

        assertThat(failures)
                .as("EntityId fields missing EntityIdConverter:\n%s", String.join("\n", failures))
                .isEmpty();
    }

    private static List<Class<?>> findEntityClasses() throws ClassNotFoundException {
        final var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));

        final var classes = new ArrayList<Class<?>>();
        for (final var beanDefinition : scanner.findCandidateComponents(DOMAIN_PACKAGE)) {
            classes.add(Class.forName(beanDefinition.getBeanClassName()));
        }

        return classes;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        final var fields = new ArrayList<Field>();

        for (var current = clazz; current != null && !Object.class.equals(current); current = current.getSuperclass()) {
            fields.addAll(List.of(current.getDeclaredFields()));
        }

        return fields;
    }

    private static boolean shouldSkip(Field field) {
        final var modifiers = field.getModifiers();

        return field.isSynthetic() || Modifier.isStatic(modifiers) || field.isAnnotationPresent(Transient.class);
    }
}
