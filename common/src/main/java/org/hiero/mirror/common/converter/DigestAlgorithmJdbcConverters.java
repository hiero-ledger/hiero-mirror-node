// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

public final class DigestAlgorithmJdbcConverters {

    private DigestAlgorithmJdbcConverters() {}

    @WritingConverter
    public static class DigestAlgorithmToJdbcValue implements Converter<DigestAlgorithm, JdbcValue> {
        @Override
        public JdbcValue convert(DigestAlgorithm source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.INTEGER);
            }
            return JdbcValue.of(source.ordinal(), JDBCType.INTEGER);
        }
    }

    @ReadingConverter
    public static class IntegerToDigestAlgorithm implements Converter<Integer, DigestAlgorithm> {
        @Override
        public DigestAlgorithm convert(Integer source) {
            if (source == null) {
                return null;
            }

            DigestAlgorithm[] values = DigestAlgorithm.values();
            if (source < 0 || source >= values.length) {
                return null;
            }

            return values[source];
        }
    }
}
