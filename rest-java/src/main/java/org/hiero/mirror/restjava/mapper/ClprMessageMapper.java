// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import java.util.HexFormat;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface ClprMessageMapper extends CollectionMapper<ClprMessageDto, org.hiero.mirror.rest.model.ClprMessage> {

    String QUALIFIER_HEX = "clprHex";

    @Mapping(source = "consensusTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "messageData", target = "messageData", qualifiedByName = QUALIFIER_HEX)
    @Mapping(source = "messageType", target = "type")
    @Mapping(source = "sender", target = "sender", qualifiedByName = QUALIFIER_HEX)
    @Mapping(source = "targetApplication", target = "targetApplication", qualifiedByName = QUALIFIER_HEX)
    org.hiero.mirror.rest.model.ClprMessage map(ClprMessageDto source);

    /**
     * Unlike {@link CommonMapper#mapByteArrayToHexString}, doesn't left-pad to 32 bytes since a message payload,
     * sender and target application are arbitrary length opaque bytes.
     */
    @Named(QUALIFIER_HEX)
    default String mapHex(byte[] source) {
        if (source == null) {
            return null;
        }

        return "0x" + HexFormat.of().formatHex(source);
    }
}
