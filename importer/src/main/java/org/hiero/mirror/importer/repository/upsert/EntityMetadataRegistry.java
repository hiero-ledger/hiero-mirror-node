// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static org.hiero.mirror.importer.util.Utility.toSnakeCase;

import jakarta.inject.Named;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.exception.FieldInaccessibleException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.JdbcOperations;

@CustomLog
@Named
@RequiredArgsConstructor
public final class EntityMetadataRegistry {

    private final DBProperties dbProperties;
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

        Table table = AnnotationUtils.findAnnotation(domainClass, Table.class);
        String tableName = (table != null && StringUtils.isNotBlank(table.value()))
                ? table.value()
                : toSnakeCase(domainClass.getSimpleName());
        Set<ColumnMetadata> columnMetadata = new TreeSet<>();

        Map<String, InformationSchemaColumns> schema = getColumnSchema(tableName);

        collectColumns(schema, domainClass, false, columnMetadata);

        var entityMetadata = new EntityMetadata(tableName, upsertable, columnMetadata);
        log.debug("Creating {}", entityMetadata);
        return entityMetadata;
    }

    private void collectColumns(
            Map<String, InformationSchemaColumns> schema,
            Class<?> domainClass,
            boolean idFromParent,
            Set<ColumnMetadata> out) {
        if (domainClass == null || domainClass == Object.class) {
            return;
        }

        // Superclass fields first so subclass can override ordering deterministically
        collectColumns(schema, domainClass.getSuperclass(), idFromParent, out);

        for (Field field : domainClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())
                    || field.isAnnotationPresent(Transient.class)) {
                continue;
            }

            boolean id = idFromParent || field.isAnnotationPresent(Id.class);

            var embedded = field.getAnnotation(Embedded.class);
            if (embedded != null) {
                collectColumns(schema, field.getType(), id, out);
                continue;
            }

            out.add(columnMetadata(schema, field, id));
        }
    }

    private ColumnMetadata columnMetadata(Map<String, InformationSchemaColumns> schema, Field field, boolean id) {
        Column column = field.getAnnotation(Column.class);
        UpsertColumn upsertColumn = field.getAnnotation(UpsertColumn.class);

        String columnName = (column != null && StringUtils.isNotBlank(column.value()))
                ? toSnakeCase(column.value())
                : toSnakeCase(field.getName());

        InformationSchemaColumns columnSchema = schema.get(columnName);

        if (columnSchema == null) {
            throw new IllegalStateException("Missing information schema for " + columnName);
        }

        var getter = getter(field);
        var setter = setter(field);
        boolean updatable = !id;
        return new ColumnMetadata(
                columnSchema.getColumnDefault(),
                getter,
                id,
                columnName,
                columnSchema.isNullable(),
                setter,
                field.getType(),
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
