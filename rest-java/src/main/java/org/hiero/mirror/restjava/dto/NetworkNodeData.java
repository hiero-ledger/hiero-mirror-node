// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;

/**
 * Network node DTO representing network node API result.
 */
@Data
public final class NetworkNodeData {

    private byte[] adminKey;
    private Boolean declineReward;
    private String description;
    private String fileId;
    private ServiceEndpoint grpcProxyEndpoint;
    private Long maxStake;
    private String memo;
    private Long minStake;
    private String nodeAccountId;
    private Long nodeId;
    private String nodeCertHash;
    private String publicKey;
    private Long rewardRateStart;
    private List<ServiceEndpoint> serviceEndpoints;
    private Long stake;
    private Long stakeNotRewarded;
    private Long stakeRewarded;
    private TimestampRangeNullable stakingPeriod;
    private TimestampRange timestamp;

    /**
     * Creates a NetworkNodeDto from a NetworkNodeRow database result.
     *
     * @param row          the database row
     * @param objectMapper ObjectMapper for JSON deserialization
     * @return NetworkNodeDto instance
     */
    public static NetworkNodeData from(NetworkNodeRow row, ObjectMapper objectMapper) {
        try {
            var dto = new NetworkNodeData();

            // Direct mappings
            dto.setAdminKey(row.getAdminKey());
            dto.setDeclineReward(row.getDeclineReward());
            dto.setDescription(row.getDescription());
            dto.setFileId(row.getFileId());
            dto.setMaxStake(row.getMaxStake());
            dto.setMemo(row.getMemo());
            dto.setMinStake(row.getMinStake());
            dto.setNodeId(row.getNodeId());
            dto.setRewardRateStart(row.getRewardRateStart());
            dto.setStake(row.getStake());
            dto.setStakeNotRewarded(row.getStakeNotRewarded());
            dto.setStakeRewarded(row.getStakeRewarded());

            // Format nodeAccountId as EntityId string (0.0.X format)
            final var nodeAccountId = row.getNodeAccountId();
            dto.setNodeAccountId(
                    nodeAccountId != null
                            ? EntityId.of(Long.parseLong(nodeAccountId)).toString()
                            : null);

            // Format publicKey with 0x prefix (database stores as hex string)
            final var publicKey = row.getPublicKey();
            dto.setPublicKey(addHexPrefix(publicKey));

            // Convert nodeCertHash from byte[] to String and add 0x prefix
            // Database stores the hex string as bytes, so convert bytes to string first
            final var nodeCertHash = row.getNodeCertHash();
            final var nodeCertHashStr = nodeCertHash != null && nodeCertHash.length > 0
                    ? new String(nodeCertHash, java.nio.charset.StandardCharsets.UTF_8)
                    : null;
            dto.setNodeCertHash(addHexPrefix(nodeCertHashStr));

            // Parse grpc_proxy_endpoint JSON (JSONB column stored as string)
            final var grpcProxyEndpointJson = row.getGrpcProxyEndpoint();
            if (grpcProxyEndpointJson != null && !grpcProxyEndpointJson.isEmpty()) {
                final var grpcProxyEndpoint = objectMapper.readValue(grpcProxyEndpointJson, ServiceEndpoint.class);
                dto.setGrpcProxyEndpoint(grpcProxyEndpoint);
            } else {
                dto.setGrpcProxyEndpoint(null);
            }

            // Parse service endpoints JSON
            final var serviceEndpointsJson = row.getServiceEndpoints();
            final var serviceEndpoints =
                    objectMapper.readValue(serviceEndpointsJson, new TypeReference<List<ServiceEndpoint>>() {});
            dto.setServiceEndpoints(serviceEndpoints);

            // Set staking period from staking period timestamp
            dto.setStakingPeriod(getStakingPeriod(row.getStakingPeriod()));

            // Set timestamp range from address book start/end timestamps
            final var timestamp = new TimestampRange();
            timestamp.setFrom(DomainUtils.toTimestamp(row.getStartConsensusTimestamp()));
            row.getEndConsensusTimestamp();
            timestamp.setTo(DomainUtils.toTimestamp(row.getEndConsensusTimestamp()));
            dto.setTimestamp(timestamp);

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NetworkNodeDto from NetworkNodeRow", e);
        }
    }

    /**
     * Adds "0x" prefix to hex string if not already present.
     *
     * @param hexData hex string
     * @return hex string with "0x" prefix
     */
    private static String addHexPrefix(String hexData) {
        if (hexData == null || hexData.isEmpty()) {
            return "0x";
        }
        return hexData.startsWith("0x") ? hexData : "0x" + hexData;
    }

    /**
     * Gets staking period object from staking period timestamp.
     *
     * @param stakingPeriod staking period start timestamp
     * @return TimestampRangeNullable with from/to, or null if input is null
     */
    private static TimestampRangeNullable getStakingPeriod(Long stakingPeriod) {
        if (stakingPeriod == null) {
            return null;
        }

        // Add 1 nanosecond to staking period start
        long stakingPeriodStart = stakingPeriod + 1L;

        var period = new TimestampRangeNullable();
        period.setFrom(DomainUtils.toTimestamp(stakingPeriodStart));
        // Add one day (86400 seconds * 1_000_000_000 nanos/second)
        period.setTo(DomainUtils.toTimestamp(stakingPeriodStart + (86400L * DomainUtils.NANOS_PER_SECOND)));
        return period;
    }
}
