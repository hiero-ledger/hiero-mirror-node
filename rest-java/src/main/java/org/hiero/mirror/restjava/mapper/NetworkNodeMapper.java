// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.util.List;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        config = MapperConfiguration.class,
        uses = {CommonMapper.class})
public interface NetworkNodeMapper {

    @Mapping(target = "adminKey", source = "adminKey", ignore = true)
    @Mapping(target = "declineReward", source = "declineReward")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "fileId", source = "fileId")
    @Mapping(target = "grpcProxyEndpoint", source = "grpcProxyEndpoint")
    @Mapping(target = "maxStake", expression = "java(mapStake(source.getMaxStake()))")
    @Mapping(target = "memo", source = "memo")
    @Mapping(target = "minStake", expression = "java(mapStake(source.getMinStake()))")
    @Mapping(target = "nodeAccountId", source = "nodeAccountId")
    @Mapping(target = "nodeId", source = "nodeId")
    @Mapping(target = "nodeCertHash", source = "nodeCertHash")
    @Mapping(target = "publicKey", source = "publicKey")
    @Mapping(target = "rewardRateStart", source = "rewardRateStart")
    @Mapping(target = "serviceEndpoints", source = "serviceEndpoints")
    @Mapping(target = "stake", source = "stake")
    @Mapping(target = "stakeNotRewarded", expression = "java(mapStake(source.getStakeNotRewarded()))")
    @Mapping(target = "stakeRewarded", source = "stakeRewarded")
    @Mapping(target = "stakingPeriod", source = "stakingPeriod")
    @Mapping(target = "timestamp", source = "timestamp")
    NetworkNode mapInternal(NetworkNodeDto source);

    default NetworkNode map(NetworkNodeDto source, CommonMapper commonMapper) {
        var node = mapInternal(source);
        node.setAdminKey(commonMapper.mapKey(source.getAdminKey()));
        return node;
    }

    default NetworkNodesResponse mapToResponse(List<NetworkNode> nodes, Links links) {
        var response = new NetworkNodesResponse();
        response.setNodes(nodes);
        response.setLinks(links);
        return response;
    }

    default Long mapStake(Long stake) {
        if (stake == null || stake == -1L) {
            return null;
        }
        return stake;
    }
}
