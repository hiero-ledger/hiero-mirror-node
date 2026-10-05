// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import java.sql.SQLType;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.convert.RelationResolver;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

/**
 * Binds properties backed by a native PostgreSQL enum column as {@link JDBCType#OTHER}. Spring Data JDBC skips the
 * writing converter for a null value and would otherwise resolve the column type to VARCHAR, which PostgreSQL rejects
 * against a native enum column. Overriding {@code getTargetSqlType} makes null values (and any code path that reads the
 * target SQL type rather than the converter's own {@code JdbcValue}) bind as OTHER, so no connection-level
 * {@code stringtype=unspecified} is needed for either tests or production.
 */
public class NativeEnumJdbcConverter extends MappingJdbcConverter {

    /**
     * Enum types stored as native PostgreSQL enum columns. Keep in sync with the {@code *ToJdbcValue} enum writing
     * converters. Other integer-backed enum columns are intentionally excluded because they have a not null
     * restriction. Nullable smallint enum columns, whose nulls would otherwise bind as VARCHAR, are handled
     * by {@link #SMALLINT_ENUM_TYPES} instead.
     */
    private static final Set<Class<?>> NATIVE_ENUM_TYPES = Set.of(
            ErrataType.class,
            EntityType.class,
            TokenAirdropStateEnum.class,
            TokenPauseStatusEnum.class,
            TokenSupplyTypeEnum.class,
            TokenTypeEnum.class,
            HookType.class,
            HookExtensionPoint.class);

    /**
     * Enum types stored as smallint columns (freeze_status, kyc_status). Spring Data JDBC resolves an enum property to
     * VARCHAR, so a null value would bind as VARCHAR and PostgreSQL rejects it against the smallint column. Binding the
     * target type as SMALLINT keeps null (and any path that reads the target SQL type) numeric.
     */
    private static final Set<Class<?>> SMALLINT_ENUM_TYPES =
            Set.of(TokenFreezeStatusEnum.class, TokenKycStatusEnum.class);

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
        if (NATIVE_ENUM_TYPES.contains(type)) {
            return JDBCType.OTHER;
        }
        if (SMALLINT_ENUM_TYPES.contains(type)) {
            return JDBCType.SMALLINT;
        }
        return super.getTargetSqlType(property);
    }
}
