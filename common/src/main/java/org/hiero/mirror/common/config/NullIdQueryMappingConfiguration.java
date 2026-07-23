// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.QueryMappingConfiguration;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.jdbc.core.RowMapper;

/**
 * Restores JPA's null-id semantics for query methods returning aggregates. Aggregate query without GROUP BY
 * yields a single all-NULL row when nothing matches. Hibernate mapped such a row to no entity, whereas Spring Data
 * JDBC's returns a hollow instance.
 * This wraps the default mapper so a row whose id column(s) are all NULL maps to null (and thus an
 * empty {@link java.util.Optional} / no list element).
 * Since this configuration replaces the framework's lifecycle-aware row mapper for declared queries, it must also
 * invoke {@link AfterConvertCallback}s itself (e.g. the persisted-flag tracking), or entities loaded via
 * {@code @Query} would behave differently from those loaded via derived/CRUD methods.
 */
@RequiredArgsConstructor
public final class NullIdQueryMappingConfiguration implements QueryMappingConfiguration {

    private final RelationalMappingContext mappingContext;
    private final JdbcConverter converter;
    private final QueryMappingConfiguration delegate;
    private final EntityCallbacks entityCallbacks;
    private final Map<Class<?>, RowMapper<?>> rowMappers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> RowMapper<? extends T> getRowMapper(Class<T> type) {
        var custom = delegate.getRowMapper(type);
        if (custom != null) {
            return custom;
        }

        var entity = (RelationalPersistentEntity<T>) mappingContext.getPersistentEntity(type);
        if (entity == null || entity.getIdProperty() == null) {
            return null; // Not an aggregate with an id, use the default row mapper
        }

        return (RowMapper<? extends T>) rowMappers.computeIfAbsent(type, t -> createRowMapper(entity));
    }

    private <T> RowMapper<T> createRowMapper(RelationalPersistentEntity<T> entity) {
        var delegate = new EntityRowMapper<>(entity, converter);
        var idColumns = idColumnNames(entity);
        return (rs, rowNum) -> {
            if (isIdNull(rs, idColumns)) {
                return null;
            }

            var mapped = delegate.mapRow(rs, rowNum);
            return mapped != null ? entityCallbacks.callback(AfterConvertCallback.class, mapped) : null;
        };
    }

    private List<String> idColumnNames(RelationalPersistentEntity<?> entity) {
        var idProperty = entity.getRequiredIdProperty();
        if (!idProperty.isEmbedded()) {
            return List.of(idProperty.getColumnName().getReference());
        }

        var prefix = Objects.requireNonNullElse(idProperty.getEmbeddedPrefix(), "");
        var idEntity = mappingContext.getRequiredPersistentEntity(idProperty.getType());
        var columns = new ArrayList<String>();
        idEntity.doWithProperties((PropertyHandler<RelationalPersistentProperty>)
                property -> columns.add(prefix + property.getColumnName().getReference()));
        return columns;
    }

    /*
     * The id is considered NULL only when every id column is present in the result set and holds SQL NULL. Queries
     * that don't select the id column(s) fall through to the default mapping.
     */
    private static boolean isIdNull(ResultSet resultSet, List<String> idColumns) throws SQLException {
        for (var column : idColumns) {
            int index;
            try {
                index = resultSet.findColumn(column);
            } catch (SQLException e) {
                return false;
            }

            if (resultSet.getObject(index) != null) {
                return false;
            }
        }

        return true;
    }
}
