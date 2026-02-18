// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Key;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface NetworkNodeMapper extends CollectionMapper<NetworkNodeDto, NetworkNode> {

    CommonMapper COMMON_MAPPER = new CommonMapperImpl();

    @Override
    @Mapping(target = "adminKey", qualifiedByName = "mapKey")
    @Mapping(target = "fileId", qualifiedByName = "mapEntityId")
    @Mapping(
            target = "nodeAccountId",
            expression =
                    "java(row.getNodeAccountId() != null ? mapEntityId(Long.parseLong(row.getNodeAccountId())) : null)")
    @Mapping(target = "publicKey", qualifiedByName = "addHexPrefix")
    @Mapping(target = "nodeCertHash", qualifiedByName = "mapNodeCertHash")
    @Mapping(target = "maxStake", qualifiedByName = "mapStake")
    @Mapping(target = "minStake", qualifiedByName = "mapStake")
    @Mapping(target = "stakeNotRewarded", qualifiedByName = "mapStake")
    @Mapping(target = "stakingPeriod", expression = "java(mapStakingPeriod(row.getStakingPeriod()))")
    @Mapping(target = "timestamp", expression = "java(mapTimestampRange(row))")
    NetworkNode map(NetworkNodeDto row);

    default NetworkNodesResponse mapToResponse(List<NetworkNode> nodes, Links links) {
        var response = new NetworkNodesResponse();
        response.setNodes(nodes);
        response.setLinks(links);
        return response;
    }

    @Named("mapKey")
    default Key mapKey(byte[] bytes) {
        return COMMON_MAPPER.mapKey(bytes);
    }

    @Named("mapEntityId")
    default String mapEntityId(Long id) {
        return COMMON_MAPPER.mapEntityId(id);
    }

    default TimestampRange mapTimestampRange(NetworkNodeDto row) {
        if (row == null) {
            return null;
        }
        return new TimestampRange()
                .from(COMMON_MAPPER.mapTimestamp(row.getStartConsensusTimestamp()))
                .to(COMMON_MAPPER.mapTimestamp(row.getEndConsensusTimestamp()));
    }

    @Named("mapStake")
    default Long mapStake(Long stake) {
        if (stake == null || stake == -1L) {
            return null;
        }
        return stake;
    }

    @Named("addHexPrefix")
    default String addHexPrefix(String hexData) {
        if (hexData == null || hexData.isEmpty()) {
            return "0x";
        }
        return hexData.startsWith("0x") ? hexData : "0x" + hexData;
    }

    @Named("mapNodeCertHash")
    default String mapNodeCertHash(byte[] nodeCertHash) {
        if (nodeCertHash == null || nodeCertHash.length == 0) {
            return "0x";
        }
        return addHexPrefix(new String(nodeCertHash, StandardCharsets.UTF_8));
    }

    default TimestampRangeNullable mapStakingPeriod(Long stakingPeriod) {
        if (stakingPeriod == null) {
            return null;
        }
        final var from = stakingPeriod + 1L;
        return new TimestampRangeNullable()
                .from(DomainUtils.toTimestamp(from))
                .to(DomainUtils.toTimestamp(from + (CommonMapper.SECONDS_PER_DAY * DomainUtils.NANOS_PER_SECOND)));
    }
}
