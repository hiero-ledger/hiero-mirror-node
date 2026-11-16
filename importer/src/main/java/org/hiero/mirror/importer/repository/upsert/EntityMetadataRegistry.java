// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import jakarta.inject.Named;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.exception.FieldInaccessibleException;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Uses the same mapped properties and column names as JDBC, including embedded IDs exposed only through accessors.
 * Transient backing fields are deliberately not inspected: their values are mapped through the embedded property.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public final class EntityMetadataRegistry {

    private final DBProperties dbProperties;
    private final Map<Class<?>, EntityMetadata> domainEntityMetadata = new ConcurrentHashMap<>();
    private final JdbcOperations jdbcOperations;
    private final RelationalMappingContext mappingContext;

    public EntityMetadata lookup(Class<?> domainClass) {
        return domainEntityMetadata.computeIfAbsent(domainClass, this::create);
    }

    private EntityMetadata create(Class<?> domainClass) {
        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);

        if (upsertable == null) {
            throw new UnsupportedOperationException("Class is not annotated with @Upsertable: " + domainClass);
        }

        var entity = mappingContext.getRequiredPersistentEntity(domainClass);
        String tableName = entity.getTableName().getReference();
        Set<ColumnMetadata> columnMetadata = new TreeSet<>();
        Map<String, InformationSchemaColumns> schema = getColumnSchema(tableName);

        collectColumns(schema, entity, false, "", List.of(), columnMetadata);
        if (columnMetadata.stream().noneMatch(ColumnMetadata::isId)) {
            throw new IllegalStateException("No mapped ID columns for " + domainClass.getName());
        }

        var entityMetadata = new EntityMetadata(tableName, upsertable, columnMetadata);
        log.debug("Creating {}", entityMetadata);
        return entityMetadata;
    }

    private void collectColumns(
            Map<String, InformationSchemaColumns> schema,
            RelationalPersistentEntity<?> entity,
            boolean idFromParent,
            String prefix,
            List<RelationalPersistentProperty> parentPath,
            Set<ColumnMetadata> out) {
        for (var property : entity) {
            var path = new ArrayList<>(parentPath);
            path.add(property);
            boolean id = idFromParent || property.isIdProperty();

            if (property.isEmbedded()) {
                collectColumns(
                        schema,
                        mappingContext.getRequiredPersistentEntity(property.getType()),
                        id,
                        prefix + Objects.requireNonNullElse(property.getEmbeddedPrefix(), ""),
                        path,
                        out);
            } else {
                String columnName = prefix + property.getColumnName().getReference();
                if (!out.add(columnMetadata(schema, path, id, columnName))) {
                    throw new IllegalStateException("Multiple mapped properties for column " + columnName);
                }
            }
        }
    }

    private ColumnMetadata columnMetadata(
            Map<String, InformationSchemaColumns> schema,
            List<RelationalPersistentProperty> path,
            boolean id,
            String columnName) {
        InformationSchemaColumns columnSchema = schema.get(columnName);
        if (columnSchema == null) {
            throw new IllegalStateException("Missing information schema for " + columnName);
        }

        var property = path.getLast();
        var getters = path.stream()
                .map(RelationalPersistentProperty::getRequiredGetter)
                .toList();
        var setters = path.stream()
                .map(RelationalPersistentProperty::getRequiredSetter)
                .toList();
        getters.forEach(method -> method.setAccessible(true));
        setters.forEach(method -> method.setAccessible(true));

        return new ColumnMetadata(
                columnSchema.getColumnDefault(),
                target -> getValue(target, getters),
                id,
                columnName,
                columnSchema.isNullable(),
                (target, value) -> setValue(target, value, getters, setters, 0),
                property.getType(),
                !id && path.stream().noneMatch(RelationalPersistentProperty::isInsertOnly),
                property.findAnnotation(UpsertColumn.class));
    }

    private Object getValue(Object target, List<Method> getters) {
        try {
            Object value = target;
            for (var getter : getters) {
                if (value == null) {
                    return null;
                }
                value = getter.invoke(value);
            }
            return value;
        } catch (ReflectiveOperationException e) {
            throw new FieldInaccessibleException(e);
        }
    }

    private void setValue(Object target, Object value, List<Method> getters, List<Method> setters, int index) {
        try {
            var setter = setters.get(index);
            if (index == setters.size() - 1) {
                setter.invoke(target, value);
                return;
            }

            var getter = getters.get(index);
            var embedded = getter.invoke(target);
            if (embedded == null) {
                embedded = BeanUtils.instantiateClass(getter.getReturnType());
            }
            setValue(embedded, value, getters, setters, index + 1);
            // A computed ID getter returns a copy. Write it back through setId() to update the aggregate's fields.
            setter.invoke(target, embedded);
        } catch (ReflectiveOperationException e) {
            throw new FieldInaccessibleException(e);
        }
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

    @Data
    static class InformationSchemaColumns {
        private String columnName;
        private String columnDefault;
        private boolean nullable;
    }
}
