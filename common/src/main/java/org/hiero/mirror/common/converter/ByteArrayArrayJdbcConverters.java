// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.HexFormat;
import org.hiero.mirror.common.domain.transaction.MaxCustomFeesHolder;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/**
 * Maps Postgres bytea[] columns (transaction.max_custom_fees) via {@link MaxCustomFeesHolder}. A bare
 * {@code byte[][]} property doesn't work: Spring Data JDBC derives the column and SQL type of array properties from
 * the component type (BINARY for byte[]), binding parameters - including nulls, which never reach a converter - as
 * bytea instead of bytea[].
 */
public final class ByteArrayArrayJdbcConverters {

    private ByteArrayArrayJdbcConverters() {}

    @WritingConverter
    public static final class MaxCustomFeesHolderToJdbcValue implements Converter<MaxCustomFeesHolder, JdbcValue> {

        private static final HexFormat HEX = HexFormat.of();

        @Override
        public JdbcValue convert(MaxCustomFeesHolder source) {
            final var items = source.items();
            if (items == null) {
                return JdbcValue.of(null, JDBCType.OTHER);
            }

            final var sb = new StringBuilder("{");
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }

                if (items[i] == null) {
                    sb.append("NULL");
                } else {
                    sb.append("\"\\\\x").append(HEX.formatHex(items[i])).append('"');
                }
            }
            sb.append('}');

            try {
                final var pgObject = new PGobject();
                pgObject.setType("bytea[]");
                pgObject.setValue(sb.toString());
                return JdbcValue.of(pgObject, JDBCType.OTHER);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create bytea[] PGobject", e);
            }
        }
    }

    /**
     * Spring Data JDBC unwraps {@link java.sql.Array} column values via {@code Array.getArray()} before conversion, so
     * pgjdbc's {@code byte[][]} for a bytea[] column is the source type here.
     */
    @ReadingConverter
    public static final class ByteArrayArrayToMaxCustomFeesHolder implements Converter<byte[][], MaxCustomFeesHolder> {

        @Override
        public MaxCustomFeesHolder convert(byte[][] source) {
            return MaxCustomFeesHolder.of(source);
        }
    }
}
