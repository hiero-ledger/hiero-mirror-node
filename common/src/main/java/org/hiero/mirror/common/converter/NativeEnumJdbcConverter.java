// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import java.sql.SQLType;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
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
     * converters. Integer-backed enums (freeze_status, kyc_status, digest_algorithm, reconciliation_status) are
     * intentionally excluded — their columns are smallint/int, so their nulls must keep the numeric target type.
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

    public NativeEnumJdbcConverter(
            RelationalMappingContext context,
            RelationResolver relationResolver,
            CustomConversions conversions,
            JdbcTypeFactory typeFactory) {
        super(context, relationResolver, conversions, typeFactory);
    }

    @Override
    public SQLType getTargetSqlType(RelationalPersistentProperty property) {
        return NATIVE_ENUM_TYPES.contains(property.getActualType()) ? JDBCType.OTHER : super.getTargetSqlType(property);
    }
}
