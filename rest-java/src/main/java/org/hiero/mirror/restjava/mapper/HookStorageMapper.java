// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.rest.model.HookStorage;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface HookStorageMapper extends CollectionMapper<HookStorageSlot, HookStorage> {
    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    HookStorage map(HookStorageSlot source);
}
