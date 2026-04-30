// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * JDBC reading converters for JSONB and PostgreSQL array columns so Spring Data JDBC maps them as simple values instead of
 * relational aggregates (which would otherwise trigger join generation and fail with {@code IdColumnInfo must not be
 * null}).
 */
public final class JsonbReadingConverters {

    private JsonbReadingConverters() {}

    @ReadingConverter
    public static final class PgobjectToRegisteredServiceEndpointList
            implements Converter<PGobject, List<RegisteredServiceEndpoint>> {
        @Override
        public List<RegisteredServiceEndpoint> convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(
                        source.getValue(), new TypeReference<List<RegisteredServiceEndpoint>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Some JDBC paths expose JSON/JSONB as a plain string.
     */
    @ReadingConverter
    public static final class StringToRegisteredServiceEndpointList
            implements Converter<String, List<RegisteredServiceEndpoint>> {
        @Override
        public List<RegisteredServiceEndpoint> convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(source, new TypeReference<List<RegisteredServiceEndpoint>>() {});
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class SqlArrayToShortList implements Converter<Array, List<Short>> {
        @Override
        public List<Short> convert(Array source) {
            if (source == null) {
                return null;
            }
            try {
                return arrayObjectToShortList(source.getArray());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        private static List<Short> arrayObjectToShortList(Object arr) {
            if (arr == null) {
                return null;
            }
            if (arr instanceof Short[] shortArr) {
                return Arrays.asList(shortArr);
            }
            if (arr instanceof Object[] objArr) {
                var list = new ArrayList<Short>(objArr.length);
                for (Object o : objArr) {
                    if (o != null) {
                        list.add(((Number) o).shortValue());
                    }
                }
                return list;
            }
            throw new IllegalStateException("Unsupported PostgreSQL array component type: " + arr.getClass());
        }
    }
}
