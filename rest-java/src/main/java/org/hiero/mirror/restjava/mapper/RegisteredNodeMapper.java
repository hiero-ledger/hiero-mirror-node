// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

@Mapper(config = MapperConfiguration.class)
public interface RegisteredNodeMapper
        extends CollectionMapper<RegisteredNode, org.hiero.mirror.rest.model.RegisteredNode> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "serviceEndpoints", expression = "java(mapServiceEndpoints(node.getServiceEndpoints()))")
    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.RegisteredNode map(RegisteredNode node);

    default List<org.hiero.mirror.rest.model.RegisteredServiceEndpoint> mapServiceEndpoints(
            List<RegisteredServiceEndpoint> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toRegisteredServiceEndpoint).toList();
    }

    org.hiero.mirror.rest.model.RegisteredServiceEndpoint toRegisteredServiceEndpoint(
            RegisteredServiceEndpoint endpoint);

    @ValueMapping(source = "UNKNOWN", target = "GENERAL_SERVICE")
    org.hiero.mirror.rest.model.RegisteredNodeType mapDomainRegisteredNodeType(
            org.hiero.mirror.common.domain.node.RegisteredNodeType source);
}
