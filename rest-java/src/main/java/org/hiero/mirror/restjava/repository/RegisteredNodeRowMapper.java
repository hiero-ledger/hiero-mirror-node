// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Range;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.converter.PGobjectToRangeReadingConverter;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps {@code registered_node} rows without Spring Data JDBC aggregate resolution.
 */
public final class RegisteredNodeRowMapper implements RowMapper<RegisteredNode> {

    private static final TypeReference<List<RegisteredServiceEndpoint>> ENDPOINT_LIST_TYPE = new TypeReference<>() {};

    private static final PGobjectToRangeReadingConverter RANGE_READING = new PGobjectToRangeReadingConverter();

    @Override
    public RegisteredNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RegisteredNode.builder()
                .adminKey(rs.getBytes("admin_key"))
                .createdTimestamp(getNullableLong(rs, "created_timestamp"))
                .deleted(rs.getBoolean("deleted"))
                .description(rs.getString("description"))
                .registeredNodeId(rs.getLong("registered_node_id"))
                .serviceEndpoints(parseServiceEndpoints(rs.getObject("service_endpoints")))
                .timestampRange(parseTimestampRange(rs.getObject("timestamp_range")))
                .type(parseType(rs.getObject("type")))
                .build();
    }

    private static @Nullable Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static @Nullable List<RegisteredServiceEndpoint> parseServiceEndpoints(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof PGobject pg) {
                String json = pg.getValue();
                if (json == null || json.isEmpty()) {
                    return null;
                }
                return ObjectToStringSerializer.OBJECT_MAPPER.readValue(json, ENDPOINT_LIST_TYPE);
            }
            if (value instanceof String s) {
                if (s.isEmpty()) {
                    return null;
                }
                return ObjectToStringSerializer.OBJECT_MAPPER.readValue(s, ENDPOINT_LIST_TYPE);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("Unsupported JDBC type for service_endpoints: "
                + value.getClass().getName());
    }

    private static @Nullable Range<Long> parseTimestampRange(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof PGobject pg) {
            return RANGE_READING.convert(pg);
        }
        throw new IllegalStateException(
                "Unsupported JDBC type for timestamp_range: " + value.getClass().getName());
    }

    private static @Nullable List<Short> parseType(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Array array) {
            return arrayToShortList(array.getArray());
        }
        throw new IllegalStateException(
                "Unsupported JDBC type for type: " + value.getClass().getName());
    }

    private static @Nullable List<Short> arrayToShortList(Object arr) {
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
