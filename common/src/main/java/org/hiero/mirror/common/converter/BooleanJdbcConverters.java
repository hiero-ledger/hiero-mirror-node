// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Restores JPA/Hibernate parity for {@code boolean} properties backed by a boolean column.
 * Spring Data JDBC reads column values through {@code ResultSet.getObject}, whose runtime type depends on the JDBC
 * driver and platform. On the JVM pgjdbc returns a Boolean for a bool column, so the value is assigned
 * to the property directly. Under GraalVM native image the same column can come back as an Integer, and with no
 * registered Integer to Boolean conversion the value reaches the primitive boolean field accessor and
 * fails with IllegalArgumentException: Can not set boolean field ... to java.lang.Integer.
 * This converter reproduces Hibernate's BooleanJavaType behaviour of accepting any Number (non-zero meaning true)
 * so entities load identically on both platforms.
 */
public final class BooleanJdbcConverters {

    private BooleanJdbcConverters() {}

    @ReadingConverter
    public static class IntegerToBoolean implements Converter<Integer, Boolean> {
        @Override
        public Boolean convert(Integer source) {
            return source == null ? null : source != 0;
        }
    }
}
