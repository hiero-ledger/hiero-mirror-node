// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(config = MapperConfiguration.class)
public abstract class NetworkNodeMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CommonMapper commonMapper;

    /**
     * Maps a NetworkNodeRow from the database to a NetworkNode API response model.
     *
     * @param row the database row
     * @return NetworkNode instance
     */
    public NetworkNode map(NetworkNodeRow row) {
        try {
            var node = new NetworkNode();

            // Direct mappings
            node.setDeclineReward(row.getDeclineReward());
            node.setDescription(row.getDescription());
            node.setMemo(row.getMemo());
            node.setNodeId(row.getNodeId());
            node.setRewardRateStart(row.getRewardRateStart());
            node.setStake(row.getStake());
            node.setStakeRewarded(row.getStakeRewarded());

            // Map adminKey using CommonMapper
            node.setAdminKey(commonMapper.mapKey(row.getAdminKey()));

            // Format fileId as EntityId string (0.0.X format)
            final var fileId = row.getFileId();
            node.setFileId(fileId != null ? EntityId.of(fileId).toString() : null);

            // Format nodeAccountId as EntityId string (0.0.X format)
            final var nodeAccountId = row.getNodeAccountId();
            node.setNodeAccountId(
                    nodeAccountId != null
                            ? EntityId.of(Long.parseLong(nodeAccountId)).toString()
                            : null);

            // Format publicKey with 0x prefix
            node.setPublicKey(addHexPrefix(row.getPublicKey()));

            // Convert nodeCertHash from byte[] to String and add 0x prefix
            final var nodeCertHash = row.getNodeCertHash();
            final var nodeCertHashStr = nodeCertHash != null && nodeCertHash.length > 0
                    ? new String(nodeCertHash, StandardCharsets.UTF_8)
                    : null;
            node.setNodeCertHash(addHexPrefix(nodeCertHashStr));

            // Parse grpc_proxy_endpoint JSON (JSONB column stored as string)
            final var grpcProxyEndpointJson = row.getGrpcProxyEndpoint();
            if (grpcProxyEndpointJson != null && !grpcProxyEndpointJson.isEmpty()) {
                node.setGrpcProxyEndpoint(objectMapper.readValue(grpcProxyEndpointJson, ServiceEndpoint.class));
            }

            // Parse service endpoints JSON
            final var serviceEndpointsJson = row.getServiceEndpoints();
            final var serviceEndpoints =
                    objectMapper.readValue(serviceEndpointsJson, new TypeReference<List<ServiceEndpoint>>() {});
            node.setServiceEndpoints(serviceEndpoints);

            // Map stake values, converting -1 to null
            node.setMaxStake(mapStake(row.getMaxStake()));
            node.setMinStake(mapStake(row.getMinStake()));
            node.setStakeNotRewarded(mapStake(row.getStakeNotRewarded()));

            // Set staking period from staking period timestamp
            node.setStakingPeriod(mapStakingPeriod(row.getStakingPeriod()));

            // Set timestamp range from address book start/end timestamps
            node.setTimestamp(mapTimestampRange(row.getStartConsensusTimestamp(), row.getEndConsensusTimestamp()));

            return node;
        } catch (Exception e) {
            throw new RuntimeException("Failed to map NetworkNodeRow to NetworkNode", e);
        }
    }

    public NetworkNodesResponse mapToResponse(List<NetworkNode> nodes, Links links) {
        var response = new NetworkNodesResponse();
        response.setNodes(nodes);
        response.setLinks(links);
        return response;
    }

    /**
     * Maps stake value, converting -1 to null as per API contract.
     */
    protected Long mapStake(Long stake) {
        if (stake == null || stake == -1L) {
            return null;
        }
        return stake;
    }

    /**
     * Adds "0x" prefix to hex string if not already present.
     */
    protected String addHexPrefix(String hexData) {
        if (hexData == null || hexData.isEmpty()) {
            return "0x";
        }
        return hexData.startsWith("0x") ? hexData : "0x" + hexData;
    }

    /**
     * Maps staking period timestamp to TimestampRangeNullable.
     */
    protected TimestampRangeNullable mapStakingPeriod(Long stakingPeriod) {
        if (stakingPeriod == null) {
            return null;
        }

        // Add 1 nanosecond to staking period start
        final var stakingPeriodStart = stakingPeriod + 1L;

        var period = new TimestampRangeNullable();
        period.setFrom(DomainUtils.toTimestamp(stakingPeriodStart));
        // Add one day (86400 seconds * 1_000_000_000 nanos/second)
        period.setTo(DomainUtils.toTimestamp(stakingPeriodStart + (86400L * DomainUtils.NANOS_PER_SECOND)));
        return period;
    }

    /**
     * Maps start and end consensus timestamps to TimestampRange.
     */
    protected TimestampRange mapTimestampRange(Long startTimestamp, Long endTimestamp) {
        var timestamp = new TimestampRange();
        timestamp.setFrom(DomainUtils.toTimestamp(startTimestamp));
        timestamp.setTo(endTimestamp != null ? DomainUtils.toTimestamp(endTimestamp) : null);
        return timestamp;
    }
}
