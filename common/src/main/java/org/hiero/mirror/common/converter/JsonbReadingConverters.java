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
import org.hiero.mirror.common.domain.node.ServiceEndpointsHolder;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FixedFeesHolder;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.FractionalFeesHolder;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.RoyaltyFeesHolder;
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

    @ReadingConverter
    public static final class PgobjectToServiceEndpointsHolder implements Converter<PGobject, ServiceEndpointsHolder> {

        private static final PgobjectToRegisteredServiceEndpointList LIST =
                new PgobjectToRegisteredServiceEndpointList();

        @Override
        public ServiceEndpointsHolder convert(PGobject source) {
            return ServiceEndpointsHolder.of(LIST.convert(source));
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

    @ReadingConverter
    public static final class PgobjectToFixedFeesHolder implements Converter<PGobject, FixedFeesHolder> {

        @Override
        public FixedFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<FixedFee>>() {});
                return FixedFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToFixedFeesHolder implements Converter<String, FixedFeesHolder> {

        @Override
        public FixedFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<FixedFee>>() {});
                return FixedFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToFractionalFeesHolder implements Converter<PGobject, FractionalFeesHolder> {

        @Override
        public FractionalFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<FractionalFee>>() {});
                return FractionalFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToFractionalFeesHolder implements Converter<String, FractionalFeesHolder> {

        @Override
        public FractionalFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<FractionalFee>>() {});
                return FractionalFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class PgobjectToRoyaltyFeesHolder implements Converter<PGobject, RoyaltyFeesHolder> {

        @Override
        public RoyaltyFeesHolder convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source.getValue(), new TypeReference<List<RoyaltyFee>>() {});
                return RoyaltyFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ReadingConverter
    public static final class StringToRoyaltyFeesHolder implements Converter<String, RoyaltyFeesHolder> {

        @Override
        public RoyaltyFeesHolder convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            try {
                var list = OBJECT_MAPPER.readValue(source, new TypeReference<List<RoyaltyFee>>() {});
                return RoyaltyFeesHolder.of(list);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
