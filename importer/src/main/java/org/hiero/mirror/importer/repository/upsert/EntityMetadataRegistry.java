// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static org.hiero.mirror.importer.util.Utility.toSnakeCase;

import jakarta.inject.Named;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.metamodel.model.domain.SingularPersistentAttribute;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.exception.FieldInaccessibleException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.JdbcOperations;

@CustomLog
@Named
@RequiredArgsConstructor
public final class EntityMetadataRegistry {

    private final DBProperties dbProperties;
    private final EntityManager entityManager;
    private final Map<Class<?>, EntityMetadata> domainEntityMetadata = new ConcurrentHashMap<>();
    private final JdbcOperations jdbcOperations;

    public EntityMetadata lookup(Class<?> domainClass) {
        return domainEntityMetadata.computeIfAbsent(domainClass, this::create);
    }

    private EntityMetadata create(Class<?> domainClass) {
        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);

        if (upsertable == null) {
            throw new UnsupportedOperationException("Class is not annotated with @Upsertable: " + domainClass);
        }

        EntityType<?> entityType = entityManager.getMetamodel().entity(domainClass);
        Table table = AnnotationUtils.findAnnotation(domainClass, Table.class);
        String tableName = table != null ? table.name() : toSnakeCase(entityType.getName());
        Set<String> idAttributes = getIdAttributes(entityType);
        Set<ColumnMetadata> columnMetadata = new TreeSet<>();

        Map<String, InformationSchemaColumns> schema = getColumnSchema(tableName);

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            boolean id = idAttributes.contains(attribute.getName());

            if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
                var persistentAttribute = (SingularPersistentAttribute) attribute;
                var embeddableType = (EmbeddableType<?>) persistentAttribute.getType();
                var overrides = attributeOverrides((Field) attribute.getJavaMember());
                embeddableType
                        .getDeclaredSingularAttributes()
                        .forEach(a -> columnMetadata.add(columnMetadata(schema, a, id, overrides.get(a.getName()))));
            } else {
                columnMetadata.add(columnMetadata(schema, attribute, id, null));
            }
        }

        var entityMetadata = new EntityMetadata(tableName, upsertable, columnMetadata);
        log.debug("Creating {}", entityMetadata);
        return entityMetadata;
    }

    /**
     * Maps each overridden attribute name declared via {@code @AttributeOverride}/{@code @AttributeOverrides} on an
     * {@code @Embedded} field to its target column name. The embeddable type's own declared attributes carry no
     * {@code @Column} of their own, so without this, every entity embedding the same {@code @Embeddable} would
     * collide on the same default column names.
     */
    private Map<String, String> attributeOverrides(Field embeddedField) {
        Map<String, String> overrides = new HashMap<>();
        AttributeOverride single = embeddedField.getAnnotation(AttributeOverride.class);
        if (single != null) {
            overrides.put(single.name(), single.column().name());
        }

        AttributeOverrides multiple = embeddedField.getAnnotation(AttributeOverrides.class);
        if (multiple != null) {
            for (AttributeOverride override : multiple.value()) {
                overrides.put(override.name(), override.column().name());
            }
        }

        return overrides;
    }

    @SuppressWarnings("java:S4276")
    private ColumnMetadata columnMetadata(
            Map<String, InformationSchemaColumns> schema,
            Attribute<?, ?> attribute,
            boolean id,
            @Nullable String columnNameOverride) {
        String name = attribute.getName();
        Field field = (Field) attribute.getJavaMember();
        Column column = field.getAnnotation(Column.class);
        UpsertColumn upsertColumn = field.getAnnotation(UpsertColumn.class);
        String columnName;
        if (StringUtils.isNotBlank(columnNameOverride)) {
            columnName = toSnakeCase(columnNameOverride);
        } else if (column != null && StringUtils.isNotBlank(column.name())) {
            columnName = toSnakeCase(column.name());
        } else {
            columnName = toSnakeCase(name);
        }

        InformationSchemaColumns columnSchema = schema.get(columnName);

        if (columnSchema == null) {
            throw new IllegalStateException("Missing information schema for " + columnName);
        }

        var getter = getter(field);
        var setter = setter(field);
        boolean updatable = !id && (column == null || column.updatable());
        return new ColumnMetadata(
                columnSchema.getColumnDefault(),
                getter,
                id,
                columnName,
                columnSchema.isNullable(),
                setter,
                attribute.getJavaType(),
                updatable,
                upsertColumn);
    }

    /*
     * Looks up column defaults in the information_schema.columns table.
     */
    private Map<String, InformationSchemaColumns> getColumnSchema(String tableName) {
        String sql = """
                select distinct column_name, regexp_replace(column_default, '::.*', '') as column_default,
                is_nullable = 'YES' as nullable from information_schema.columns
                where table_name = ? and table_schema = ?
                """;

        var columnSchemas = jdbcOperations.query(
                sql,
                (rs, rowNum) -> {
                    var columnSchema = new InformationSchemaColumns();
                    columnSchema.setColumnName(rs.getString(1));
                    columnSchema.setColumnDefault(rs.getString(2));
                    columnSchema.setNullable(rs.getBoolean(3));
                    return columnSchema;
                },
                tableName,
                dbProperties.getSchema());
        var schema = columnSchemas.stream()
                .collect(Collectors.toMap(InformationSchemaColumns::getColumnName, Function.identity()));
        if (schema.isEmpty()) {
            throw new IllegalStateException("Missing information schema for " + tableName);
        }

        return schema;
    }

    private Set<String> getIdAttributes(EntityType<?> entityType) {
        try {
            return entityType.getIdClassAttributes().stream()
                    .map(SingularAttribute::getName)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            SingularAttribute<?, ?> idAttribute = entityType.getId(Object.class);

            var attributeType = idAttribute.getPersistentAttributeType();
            if (attributeType != Attribute.PersistentAttributeType.BASIC
                    && attributeType != Attribute.PersistentAttributeType.EMBEDDED) {
                throw new UnsupportedOperationException("Unsupported ID attribute " + entityType.getName());
            }

            return Set.of(idAttribute.getName());
        }
    }

    private Function<Object, Object> getter(Field field) {
        try {
            final var prefix = field.getType().equals(boolean.class) ? "is" : "get";
            final var methodName = prefix + StringUtils.capitalize(field.getName());
            final var method = field.getDeclaringClass().getMethod(methodName);
            method.setAccessible(true);
            return value -> {
                try {
                    return method.invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new FieldInaccessibleException(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new FieldInaccessibleException(e);
        }
    }

    private BiConsumer<Object, Object> setter(Field field) {
        try {
            final var methodName = "set" + StringUtils.capitalize(field.getName());
            final var method = field.getDeclaringClass().getMethod(methodName, field.getType());
            method.setAccessible(true);
            return (target, value) -> {
                try {
                    method.invoke(target, value);
                } catch (ReflectiveOperationException e) {
                    throw new FieldInaccessibleException(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new FieldInaccessibleException(e);
        }
    }

    @Data
    static class InformationSchemaColumns {
        private String columnName;
        private String columnDefault;
        private boolean nullable;
    }
}
