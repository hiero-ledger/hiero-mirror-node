// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_HEX;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprMessageMapper extends CollectionMapper<ClprMessageDto, org.hiero.mirror.rest.model.ClprMessage> {

    @Mapping(source = "consensusTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "messageData", target = "messageData", qualifiedByName = QUALIFIER_HEX)
    @Mapping(source = "messageType", target = "type")
    @Mapping(source = "sender", target = "sender", qualifiedByName = QUALIFIER_HEX)
    @Mapping(source = "targetApplication", target = "targetApplication", qualifiedByName = QUALIFIER_HEX)
    org.hiero.mirror.rest.model.ClprMessage map(ClprMessageDto source);
}
