// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;
import org.hiero.mirror.restjava.util.FormattingUtils;

/**
 * Network node DTO representing network node API result.
 */
@Data
public class NetworkNodeDto {

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
    public static NetworkNodeDto from(NetworkNodeRow row, ObjectMapper objectMapper) {
        try {
            var dto = new NetworkNodeDto();

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
            var nodeAccountId = row.getNodeAccountId();
            dto.setNodeAccountId(
                    nodeAccountId != null
                            ? EntityId.of(Long.parseLong(nodeAccountId)).toString()
                            : null);

            // Format publicKey with 0x prefix (database stores as hex string)
            var publicKey = row.getPublicKey();
            dto.setPublicKey(FormattingUtils.addHexPrefix(publicKey));

            // Convert nodeCertHash from byte[] to String and add 0x prefix
            // Database stores the hex string as bytes, so convert bytes to string first
            var nodeCertHash = row.getNodeCertHash();
            var nodeCertHashStr = nodeCertHash != null && nodeCertHash.length > 0
                    ? new String(nodeCertHash, java.nio.charset.StandardCharsets.UTF_8)
                    : null;
            dto.setNodeCertHash(FormattingUtils.addHexPrefix(nodeCertHashStr));

            // Parse service endpoints JSON
            var serviceEndpointsJson = row.getServiceEndpoints();
            var serviceEndpoints =
                    objectMapper.readValue(serviceEndpointsJson, new TypeReference<List<ServiceEndpoint>>() {});
            dto.setServiceEndpoints(serviceEndpoints);

            // Set staking period from staking period timestamp
            dto.setStakingPeriod(FormattingUtils.getStakingPeriod(row.getStakingPeriod()));

            // Set timestamp range from address book start/end timestamps
            if (row.getStartConsensusTimestamp() != null) {
                var timestamp = new TimestampRange();
                timestamp.setFrom(FormattingUtils.nsToSecNs(row.getStartConsensusTimestamp()));
                if (row.getEndConsensusTimestamp() != null) {
                    timestamp.setTo(FormattingUtils.nsToSecNs(row.getEndConsensusTimestamp()));
                }
                dto.setTimestamp(timestamp);
            }

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NetworkNodeDto from NetworkNodeRow", e);
        }
    }
}
