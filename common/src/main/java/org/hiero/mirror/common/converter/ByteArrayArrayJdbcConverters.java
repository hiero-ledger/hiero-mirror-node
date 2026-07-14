// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.Array;
import java.sql.JDBCType;
import java.sql.SQLException;
import org.hiero.mirror.common.domain.transaction.MaxCustomFeesHolder;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/**
 * Maps Postgres {@code bytea[]} columns ({@code transaction.max_custom_fees}) via {@link MaxCustomFeesHolder}. A bare
 * {@code byte[][]} property doesn't work: Spring Data JDBC derives the column and SQL type of array properties from
 * the component type (BINARY for byte[]), binding parameters - including nulls, which never reach a converter - as
 * bytea instead of bytea[].
 */
public final class ByteArrayArrayJdbcConverters {

    private ByteArrayArrayJdbcConverters() {}

    @WritingConverter
    public static final class MaxCustomFeesHolderToJdbcValue implements Converter<MaxCustomFeesHolder, JdbcValue> {

        @Override
        public JdbcValue convert(MaxCustomFeesHolder source) {
            var items = source.items();
            if (items == null) {
                return JdbcValue.of(null, JDBCType.OTHER);
            }
            var sb = new StringBuilder("{");
            for (int i = 0; i < items.length; i++) {
                if (i > 0) sb.append(',');
                if (items[i] == null) {
                    sb.append("NULL");
                } else {
                    sb.append("\"\\\\x");
                    for (byte b : items[i]) {
                        sb.append(String.format("%02x", b & 0xFF));
                    }
                    sb.append('"');
                }
            }
            sb.append('}');
            try {
                var pgo = new PGobject();
                pgo.setType("bytea[]");
                pgo.setValue(sb.toString());
                return JdbcValue.of(pgo, JDBCType.OTHER);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create bytea[] PGobject", e);
            }
        }
    }

    @ReadingConverter
    public static final class SqlArrayToMaxCustomFeesHolder implements Converter<Array, MaxCustomFeesHolder> {

        private static final PGobjectToByteArrayArray TO_BYTE_ARRAY_ARRAY = new PGobjectToByteArrayArray();

        @Override
        public MaxCustomFeesHolder convert(Array source) {
            return MaxCustomFeesHolder.of(TO_BYTE_ARRAY_ARRAY.convert(source));
        }
    }

    @ReadingConverter
    public static final class PGobjectToByteArrayArray implements Converter<Array, byte[][]> {

        @Override
        public byte[][] convert(Array source) {
            if (source == null) {
                return null;
            }
            try {
                var array = (Object[]) source.getArray();
                if (array == null) {
                    return null;
                }
                var result = new byte[array.length][];
                for (int i = 0; i < array.length; i++) {
                    result[i] = array[i] instanceof byte[] b ? b : null;
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read bytea[] from SQL Array", e);
            }
        }
    }
}
