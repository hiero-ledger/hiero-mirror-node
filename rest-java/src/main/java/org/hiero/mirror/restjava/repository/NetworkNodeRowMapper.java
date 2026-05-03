// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/** Maps {@link NetworkNodeDto} from {@link NetworkNodeRepository#findNetworkNodes} result columns (quoted camelCase aliases). */
public final class NetworkNodeRowMapper implements RowMapper<NetworkNodeDto> {

    @Override
    public NetworkNodeDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NetworkNodeDto(
                rs.getBytes("adminKey"),
                readLongArray(rs, "associatedRegisteredNodes"),
                getNullableBoolean(rs, "declineReward"),
                rs.getString("description"),
                getNullableLong(rs, "endConsensusTimestamp"),
                getNullableLong(rs, "fileId"),
                rs.getString("grpcProxyEndpointJson"),
                getNullableLong(rs, "maxStake"),
                rs.getString("memo"),
                getNullableLong(rs, "minStake"),
                getNullableLong(rs, "nodeAccountId"),
                rs.getString("nodeCertHash"),
                getNullableLong(rs, "nodeId"),
                rs.getString("publicKey"),
                getNullableLong(rs, "rewardRateStart"),
                rs.getString("serviceEndpointsJson"),
                getNullableLong(rs, "stake"),
                getNullableLong(rs, "stakeNotRewarded"),
                getNullableLong(rs, "stakeRewarded"),
                getNullableLong(rs, "stakingPeriod"),
                getNullableLong(rs, "startConsensusTimestamp"));
    }

    private static @Nullable Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static @Nullable Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }

    private static Long @Nullable [] readLongArray(ResultSet rs, String column) throws SQLException {
        Object v = rs.getObject(column);
        if (v == null || rs.wasNull()) {
            return null;
        }
        if (!(v instanceof Array sqlArray)) {
            throw new IllegalStateException(
                    "Unsupported JDBC type for " + column + ": " + v.getClass().getName());
        }
        return arrayObjectToLongArray(sqlArray.getArray());
    }

    private static Long @Nullable [] arrayObjectToLongArray(Object arr) throws SQLException {
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
