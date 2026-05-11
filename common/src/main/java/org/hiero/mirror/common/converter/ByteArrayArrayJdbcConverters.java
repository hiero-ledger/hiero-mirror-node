// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.Array;
import java.sql.SQLException;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class ByteArrayArrayJdbcConverters {

    private ByteArrayArrayJdbcConverters() {}

    @WritingConverter
    public static final class ByteArrayArrayToPGobject implements Converter<byte[][], PGobject> {

        @Override
        public PGobject convert(byte[][] source) {
            if (source == null) {
                return null;
            }
            var sb = new StringBuilder("{");
            for (int i = 0; i < source.length; i++) {
                if (i > 0) sb.append(',');
                if (source[i] == null) {
                    sb.append("NULL");
                } else {
                    sb.append("\"\\\\x");
                    for (byte b : source[i]) {
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
                return pgo;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to create bytea[] PGobject", e);
            }
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
