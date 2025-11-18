// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface HookStorageMapper extends CollectionMapper<HookStorage, org.hiero.mirror.rest.model.HookStorage> {
    String QUALIFIER_KEY_VALUE_HEX = "keyValueHex0xPrefix";

    @Mapping(source = "modifiedTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "key", target = "key", qualifiedByName = QUALIFIER_KEY_VALUE_HEX)
    @Mapping(source = "value", target = "value", qualifiedByName = QUALIFIER_KEY_VALUE_HEX)
    org.hiero.mirror.rest.model.HookStorage map(HookStorage source);

    @Named(QUALIFIER_KEY_VALUE_HEX)
    default String mapByteArrayToHexStringWith0xPrefix(byte[] source) {
        if (source == null) {
            return null;
        }
        return "0x" + Hex.encodeHexString(source);
    }
}
