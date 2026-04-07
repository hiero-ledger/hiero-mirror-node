// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface RegisteredNodeMapper
        extends CollectionMapper<RegisteredNode, org.hiero.mirror.rest.model.RegisteredNode> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.RegisteredNode map(RegisteredNode node);
}
