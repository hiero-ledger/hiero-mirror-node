// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

final class NativeImageDomainTest {

    private static final String DOMAIN_PACKAGE = "org.hiero.mirror.common.domain";

    @Test
    void entityIdFieldsAreDiscoverable() throws Exception {
        final var entityIdFields = new ArrayList<String>();

        for (final var entityClass : findTableClasses()) {
            for (final var field : getAllFields(entityClass)) {
                if (EntityId.class.equals(field.getType()) && !shouldSkip(field)) {
                    entityIdFields.add(
                            "%s.%s".formatted(field.getDeclaringClass().getSimpleName(), field.getName()));
                }
            }
        }

        assertThat(entityIdFields)
                .as("Expected at least one EntityId field to be discoverable in @Table classes")
                .isNotEmpty();
    }

    private static List<Class<?>> findTableClasses() throws ClassNotFoundException {
        final var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Table.class));

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
