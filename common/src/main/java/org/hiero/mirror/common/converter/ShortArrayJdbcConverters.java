// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.Array;
import org.hiero.mirror.common.domain.node.RegisteredNodeTypesHolder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/** Writes/read Postgres {@code smallint[]} for {@link RegisteredNodeTypesHolder} ({@code registered_node.type}). */
public final class ShortArrayJdbcConverters {

    private ShortArrayJdbcConverters() {}

    @WritingConverter
    public static final class RegisteredNodeTypesHolderToShortArray
            implements Converter<RegisteredNodeTypesHolder, short[]> {

        @Override
        public short[] convert(RegisteredNodeTypesHolder source) {
            if (source == null) {
                return null;
            }
            var list = source.types();
            if (list.isEmpty()) {
                return new short[0];
            }
            var arr = new short[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Short v = list.get(i);
                arr[i] = v == null ? 0 : v.shortValue();
            }
            return arr;
        }
    }

    @ReadingConverter
    public static final class SqlArrayToRegisteredNodeTypesHolder
            implements Converter<Array, RegisteredNodeTypesHolder> {

        private static final JsonbReadingConverters.SqlArrayToShortList TO_SHORT_LIST =
                new JsonbReadingConverters.SqlArrayToShortList();

        @Override
        public RegisteredNodeTypesHolder convert(Array source) {
            return RegisteredNodeTypesHolder.of(TO_SHORT_LIST.convert(source));
        }
    }
}
