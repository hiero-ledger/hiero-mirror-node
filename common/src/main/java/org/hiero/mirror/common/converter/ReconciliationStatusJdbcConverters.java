// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

// Persisted as the enum ordinal in a smallint column, matching the previous JPA @Enumerated(ORDINAL) default
public final class ReconciliationStatusJdbcConverters {

    private ReconciliationStatusJdbcConverters() {}

    @WritingConverter
    public static class ReconciliationStatusToJdbcValue implements Converter<ReconciliationStatus, JdbcValue> {
        @Override
        public JdbcValue convert(ReconciliationStatus source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.SMALLINT);
            }
            return JdbcValue.of(source.ordinal(), JDBCType.SMALLINT);
        }
    }

    @ReadingConverter
    public static class IntegerToReconciliationStatus implements Converter<Integer, ReconciliationStatus> {
        @Override
        public ReconciliationStatus convert(Integer source) {
            if (source == null) {
                return null;
            }

            ReconciliationStatus[] values = ReconciliationStatus.values();
            if (source < 0 || source >= values.length) {
                return null;
            }

            return values[source];
        }
    }
}
