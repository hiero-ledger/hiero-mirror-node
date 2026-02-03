// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;

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
     * @param row the database row
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
            dto.setNodeAccountId(row.getNodeAccountId());
            dto.setNodeId(row.getNodeId());
            dto.setPublicKey(row.getPublicKey());
            dto.setRewardRateStart(row.getRewardRateStart());
            dto.setStake(row.getStake());
            dto.setStakeNotRewarded(row.getStakeNotRewarded());
            dto.setStakeRewarded(row.getStakeRewarded());

            // Convert nodeCertHash from byte[] to String
            var nodeCertHash = row.getNodeCertHash();
            dto.setNodeCertHash(nodeCertHash != null ? new String(nodeCertHash) : null);

            // Parse service endpoints JSON
            var serviceEndpointsJson = row.getServiceEndpoints();
            var serviceEndpoints =
                    objectMapper.readValue(serviceEndpointsJson, new TypeReference<List<ServiceEndpoint>>() {});
            dto.setServiceEndpoints(serviceEndpoints);

            // Set timestamp range from address book start/end timestamps
            if (row.getStartConsensusTimestamp() != null) {
                var timestamp = new TimestampRange();
                timestamp.setFrom(String.valueOf(row.getStartConsensusTimestamp()));
                if (row.getEndConsensusTimestamp() != null) {
                    timestamp.setTo(String.valueOf(row.getEndConsensusTimestamp()));
                }
                dto.setTimestamp(timestamp);
            }

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NetworkNodeDto from NetworkNodeRow", e);
        }
    }
}
