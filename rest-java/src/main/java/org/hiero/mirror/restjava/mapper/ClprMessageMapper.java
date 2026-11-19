// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.ClprMessagePayload.PayloadCase;
import org.hiero.mirror.rest.model.ClprMessage;
import org.hiero.mirror.rest.model.ClprMessageReplyStatus;
import org.hiero.mirror.rest.model.ClprMessageType;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.jspecify.annotations.Nullable;
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
    @Mapping(target = "type", expression = "java(mapMessageType(message.messageType()))")
    @Override
    ClprMessage map(ClprMessageDto message);

    /** The stored value is the proto wire number of the payload's oneof field. */
    default ClprMessageType mapMessageType(int number) {
        return switch (PayloadCase.forNumber(number)) {
            case MESSAGE -> ClprMessageType.DATA;
            case MESSAGE_REPLY -> ClprMessageType.RESPONSE;
            case CONTROL -> ClprMessageType.CONTROL;
            case null, default -> throw new IllegalStateException("Unknown CLPR message type " + number);
        };
    }

    /** The stored value is the proto wire number. */
    default ClprMessageReplyStatus mapReplyStatus(@Nullable Integer number) {
        if (number == null) {
            return null;
        }

        return ClprMessageReplyStatus.fromValue(
                com.hederahashgraph.api.proto.java.ClprMessageReplyStatus.forNumber(number)
                        .name());
    }
}
