// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hiero.mirror.common.domain.node.AssociatedRegisteredNodeIds;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/**
 * Maps Postgres {@code bigint[]} ({@code int8[]}) columns (e.g. {@code node.associated_registered_nodes}) via
 * {@link AssociatedRegisteredNodeIds}, plus helpers for {@link List}{@code <Long>} reads and {@link Long[]} projections.
 */
public final class LongArrayJdbcConverters {

    private LongArrayJdbcConverters() {}

    @WritingConverter
    public static final class AssociatedRegisteredNodeIdsToLongArray
            implements Converter<AssociatedRegisteredNodeIds, long[]> {

        @Override
        public long[] convert(AssociatedRegisteredNodeIds source) {
            if (source == null) {
                return null;
            }
            if (source.ids().isEmpty()) {
                return new long[0];
            }
            return source.ids().stream().mapToLong(Long::longValue).toArray();
        }
    }

    @ReadingConverter
    public static final class SqlArrayToAssociatedRegisteredNodeIds
            implements Converter<Array, AssociatedRegisteredNodeIds> {

        private static final SqlArrayToLongList TO_LONG_LIST = new SqlArrayToLongList();

        @Override
        public AssociatedRegisteredNodeIds convert(Array source) {
            return AssociatedRegisteredNodeIds.of(TO_LONG_LIST.convert(source));
        }
    }

    @ReadingConverter
    public static final class SqlArrayToLongList implements Converter<Array, List<Long>> {

        @Override
        public List<Long> convert(Array source) {
            if (source == null) {
                return null;
            }
            try {
                return arrayObjectToLongList(source.getArray());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        private static List<Long> arrayObjectToLongList(Object arr) {
            if (arr == null) {
                return null;
            }
            if (arr instanceof Long[] longArr) {
                return Arrays.asList(longArr);
            }
            if (arr instanceof long[] primitiveArr) {
                var list = new ArrayList<Long>(primitiveArr.length);
                for (long v : primitiveArr) {
                    list.add(v);
                }
                return list;
            }
            if (arr instanceof Object[] objArr) {
                var list = new ArrayList<Long>(objArr.length);
                for (Object o : objArr) {
                    if (o != null) {
                        list.add(((Number) o).longValue());
                    }
                }
                return list;
            }
            throw new IllegalStateException("Unsupported PostgreSQL array component type: " + arr.getClass());
        }
    }

    /** For JDBC query projections (e.g. {@code NetworkNodeDto}) that use {@code Long[]} for {@code bigint[]} columns. */
    @ReadingConverter
    public static final class SqlArrayToLongArray implements Converter<Array, Long[]> {

        @Override
        public Long[] convert(Array source) {
            if (source == null) {
                return null;
            }
            try {
                return arrayObjectToLongArray(source.getArray());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Long[] arrayObjectToLongArray(Object arr) {
            if (arr == null) {
                return null;
            }
            if (arr instanceof Long[] longArr) {
                return longArr.clone();
            }
            if (arr instanceof long[] primitiveArr) {
                var boxed = new Long[primitiveArr.length];
                for (int i = 0; i < primitiveArr.length; i++) {
                    boxed[i] = primitiveArr[i];
                }
                return boxed;
            }
            if (arr instanceof Object[] objArr) {
                var boxed = new Long[objArr.length];
                for (int i = 0; i < objArr.length; i++) {
                    boxed[i] = objArr[i] == null ? null : ((Number) objArr[i]).longValue();
                }
                return boxed;
            }
            throw new IllegalStateException("Unsupported PostgreSQL array component type: " + arr.getClass());
        }
    }
}
