// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/**
 * JDBC converters for enums persisted as their ordinal in an integer column, matching the previous JPA
 * {@code @Enumerated(ORDINAL)} mapping. Every other enum property is backed by a native PostgreSQL enum, which Spring
 * Data JDBC already writes by name and reads via {@link Enum#valueOf}; {@link NativeEnumJdbcConverter} binds both kinds
 * with the right SQL type.
 */
public final class OrdinalEnumJdbcConverters {

    // Explicit values() arrays so lookups need no reflective getEnumConstants() under native image
    static final Map<Class<?>, Enum<?>[]> TYPES = Map.of(
            DigestAlgorithm.class, DigestAlgorithm.values(),
            ReconciliationStatus.class, ReconciliationStatus.values(),
            TokenFreezeStatusEnum.class, TokenFreezeStatusEnum.values(),
            TokenKycStatusEnum.class, TokenKycStatusEnum.values());

    private OrdinalEnumJdbcConverters() {}

    @WritingConverter
    public static final class EnumToInteger implements GenericConverter {

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            final var pairs = new HashSet<ConvertiblePair>();
            for (final var type : TYPES.keySet()) {
                pairs.add(new ConvertiblePair(type, Integer.class));
            }
            return pairs;
        }

        @Override
        public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            return source == null ? null : ((Enum<?>) source).ordinal();
        }
    }

    @ReadingConverter
    public static final class IntegerToEnum implements GenericConverter {

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            final var pairs = new HashSet<ConvertiblePair>();
            for (final var type : TYPES.keySet()) {
                pairs.add(new ConvertiblePair(Integer.class, type));
            }
            return pairs;
        }

        @Override
        public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }

            final var values = TYPES.get(targetType.getType());
            final int ordinal = (Integer) source;
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
        }
    }
}
