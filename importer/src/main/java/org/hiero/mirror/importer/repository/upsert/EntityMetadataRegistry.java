// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static java.lang.invoke.MethodType.methodType;
import static org.hiero.mirror.importer.util.Utility.toSnakeCase;

import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
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
                embeddableType
                        .getDeclaredSingularAttributes()
                        .forEach(a -> columnMetadata.add(columnMetadata(schema, a, id)));
            } else {
                columnMetadata.add(columnMetadata(schema, attribute, id));
            }
        }

        var entityMetadata = new EntityMetadata(tableName, upsertable, columnMetadata);
        log.debug("Creating {}", entityMetadata);
        return entityMetadata;
    }

    @SuppressWarnings("java:S4276")
    private ColumnMetadata columnMetadata(
            Map<String, InformationSchemaColumns> schema, Attribute<?, ?> attribute, boolean id) {
        String name = attribute.getName();
        Field field = (Field) attribute.getJavaMember();
        Column column = field.getAnnotation(Column.class);
        UpsertColumn upsertColumn = field.getAnnotation(UpsertColumn.class);
        String columnName = column != null && StringUtils.isNotBlank(column.name())
                ? toSnakeCase(column.name())
                : toSnakeCase(name);

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
        String sql =
                """
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

    @SuppressWarnings("unchecked")
    private Function<Object, Object> getter(Field field) {
        try {
            String prefix = field.getType().equals(boolean.class) ? "is" : "get";
            String methodName = prefix + StringUtils.capitalize(field.getName());
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(field.getType());
            MethodHandle handle = lookup.findVirtual(field.getDeclaringClass(), methodName, type);
            MethodType functionType = handle.type();
            final var callSite = LambdaMetafactory.metafactory(
                    lookup, "apply", methodType(Function.class), functionType.erase(), handle, functionType);
            return (Function<Object, Object>) callSite.getTarget().invokeExact();
        } catch (Throwable t) {
            throw new FieldInaccessibleException(t);
        }
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<Object, Object> setter(Field field) {
        try {
            String methodName = "set" + StringUtils.capitalize(field.getName());
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(void.class, field.getType());
            MethodHandle handle = lookup.findVirtual(field.getDeclaringClass(), methodName, type);
            MethodType functionType = handle.type();
            final var callSite = LambdaMetafactory.metafactory(
                    lookup, "accept", methodType(BiConsumer.class), functionType.erase(), handle, functionType);
            return (BiConsumer<Object, Object>) callSite.getTarget().invokeExact();
        } catch (Throwable t) {
            throw new FieldInaccessibleException(t);
        }
    }

    @Data
    static class InformationSchemaColumns {
        private String columnName;
        private String columnDefault;
        private boolean nullable;
    }
}
