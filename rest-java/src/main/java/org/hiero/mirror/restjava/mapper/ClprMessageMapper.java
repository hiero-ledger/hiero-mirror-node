// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.rest.model.ClprMessage;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps messages to the API model. The control message payload isn't exposed, and the connector, sender, target
 * application and reply fields are null for the message types that don't have them.
 */
@Mapper(config = MapperConfiguration.class)
public interface ClprMessageMapper extends CollectionMapper<ClprMessageDto, ClprMessage> {

    @Mapping(target = "channelId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "connectorId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "messageData", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "runningHash", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "sender", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "targetApplication", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(source = "consensusTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "messageType", target = "type")
    @Override
    ClprMessage map(ClprMessageDto message);
}
