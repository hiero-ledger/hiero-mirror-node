// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.DelegatingDataAccessStrategy;
import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.jdbc.core.convert.InsertSubject;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.relational.core.conversion.IdValueSource;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * Preserves the JPA assigned-id insert semantics the code base was written against: Spring Data JDBC treats a
 * primitive id of zero as absent, omits the id column from the insert and expects the database to generate it. No
 * mirror node table generates ids, so any present id value (including zero) is inserted as provided.
 *
 * <p>Also restores the JPA update semantics for aggregates with an embedded composite id. Spring Data JDBC does not
 * recognize the embedded id columns as identifier columns, so it leaks them into the UPDATE SET clause. That is
 * unnecessary and, on Citus, illegal because the id column is the distribution column and cannot be modified. Marking
 * the embedded columns {@code @InsertOnlyProperty} keeps them out of SET but also nulls their WHERE binding, turning
 * every update into a silent no-op. Instead the UPDATE is generated here with the id columns in the WHERE clause and
 * out of SET, matching the prior JPA behavior.
 */
public class AssignedIdDataAccessStrategy extends DelegatingDataAccessStrategy {

    private final RelationalMappingContext mappingContext;
    private final JdbcConverter converter;
    private final NamedParameterJdbcOperations operations;
    private final IdentifierProcessing identifierProcessing;

    public AssignedIdDataAccessStrategy(
            DataAccessStrategy delegate,
            RelationalMappingContext mappingContext,
            JdbcConverter converter,
            NamedParameterJdbcOperations operations,
            Dialect dialect) {
        super(delegate);
        this.mappingContext = mappingContext;
        this.converter = converter;
        this.operations = operations;
        this.identifierProcessing = dialect.getIdentifierProcessing();
    }

    @Override
    public <T> Object insert(T objectToSave, Class<T> domainType, Identifier identifier, IdValueSource idValueSource) {
        var id = assignedId(objectToSave, domainType, idValueSource);
        if (id == null) {
            return super.insert(objectToSave, domainType, identifier, idValueSource);
        }

        super.insert(objectToSave, domainType, identifier, IdValueSource.PROVIDED);
        return id;
    }

    @Override
    public <T> Object[] insert(
            List<InsertSubject<T>> insertSubjects, Class<T> domainType, IdValueSource idValueSource) {
        var ids = insertSubjects.stream()
                .map(subject -> assignedId(subject.getInstance(), domainType, idValueSource))
                .toList();
        if (ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
            return super.insert(insertSubjects, domainType, idValueSource);
        }

        super.insert(insertSubjects, domainType, IdValueSource.PROVIDED);
        return ids.toArray();
    }

    // TODO: retest whether this override is still needed once spring-data-relational 4.1.1 ships the composite-id
    // write-SQL fix (https://github.com/spring-projects/spring-data-relational/issues/2313). As of 4.1.0,
    // super.update()
    // still emits the embedded id columns in the UPDATE SET clause, which is illegal on Citus (the id is the
    // distribution column). To verify: delegate to super.update() and run the embedded-id repository tests on the
    // v2/Citus profile.
    @Override
    public <S> boolean update(S instance, Class<S> domainType) {
        var entity = mappingContext.getRequiredPersistentEntity(domainType);
        var idProperty = entity.getIdProperty();
        if (idProperty == null || !idProperty.isEmbedded()) {
            return super.update(instance, domainType);
        }

        var accessor = entity.getPropertyAccessor(instance);
        var parameters = new MapSqlParameterSource();

        var setClauses = new ArrayList<String>();
        for (var property : entity) {
            if (entity.isIdProperty(property)
                    || property.isEntity()
                    || !property.isWritable()
                    || property.isInsertOnly()) {
                continue;
            }
            var column = property.getColumnName();
            addParameter(parameters, column, property, accessor.getProperty(property));
            setClauses.add(assignment(column));
        }

        var idEntity = mappingContext.getRequiredPersistentEntity(idProperty.getTypeInformation());
        var idAccessor = idEntity.getPropertyAccessor(accessor.getProperty(idProperty));
        var whereClauses = new ArrayList<String>();
        for (var idColumn : idEntity) {
            var column = idColumn.getColumnName();
            addParameter(parameters, column, idColumn, idAccessor.getProperty(idColumn));
            whereClauses.add(assignment(column));
        }

        var sql = "update " + entity.getQualifiedTableName().toSql(identifierProcessing) + " set "
                + String.join(", ", setClauses) + " where " + String.join(" and ", whereClauses);
        return operations.update(sql, parameters) != 0;
    }

    private void addParameter(
            MapSqlParameterSource parameters,
            SqlIdentifier column,
            RelationalPersistentProperty property,
            Object value) {
        var jdbcValue = converter.writeJdbcValue(
                value, converter.getColumnType(property), converter.getTargetSqlType(property));
        var sqlType = jdbcValue.getJdbcType();
        if (sqlType == null) {
            parameters.addValue(column.getReference(), jdbcValue.getValue());
        } else {
            parameters.addValue(column.getReference(), jdbcValue.getValue(), sqlType.getVendorTypeNumber());
        }
    }

    private String assignment(SqlIdentifier column) {
        return column.toSql(identifierProcessing) + " = :" + column.getReference();
    }

    private Object assignedId(Object instance, Class<?> domainType, IdValueSource idValueSource) {
        if (idValueSource != IdValueSource.GENERATED) {
            return null;
        }

        var entity = mappingContext.getRequiredPersistentEntity(domainType);
        if (!entity.hasIdProperty()) {
            return null;
        }

        return entity.getIdentifierAccessor(instance).getIdentifier();
    }
}
