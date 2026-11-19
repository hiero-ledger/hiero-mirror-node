// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import java.sql.SQLType;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.convert.RelationResolver;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

/**
 * Binds enum properties with the SQL type of their column. Spring Data JDBC resolves an enum property to VARCHAR and
 * skips writing converters for null values, which PostgreSQL rejects against both native enum and smallint columns.
 * Enums listed in {@link OrdinalEnumJdbcConverters} are stored as their ordinal and bind as SMALLINT; every other enum is
 * a native PostgreSQL enum and binds its name as OTHER, so no per-enum writing converter and no connection-level
 * {@code stringtype=unspecified} is needed.
 */
public class NativeEnumJdbcConverter extends MappingJdbcConverter {

    public NativeEnumJdbcConverter(
            RelationalMappingContext context,
            RelationResolver relationResolver,
            CustomConversions conversions,
            JdbcTypeFactory typeFactory) {
        super(context, relationResolver, conversions, typeFactory);
    }

    @Override
    public SQLType getTargetSqlType(RelationalPersistentProperty property) {
        final var type = property.getActualType();
        if (!type.isEnum()) {
            return super.getTargetSqlType(property);
        }

        return OrdinalEnumJdbcConverters.TYPES.containsKey(type) ? JDBCType.SMALLINT : JDBCType.OTHER;
    }
}
