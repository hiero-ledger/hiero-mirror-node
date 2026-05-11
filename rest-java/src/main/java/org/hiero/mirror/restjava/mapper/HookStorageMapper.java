// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.util.List;
import org.hiero.mirror.rest.model.HookStorage;
import org.hiero.mirror.restjava.dto.HookStorageSlot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface HookStorageMapper {

    List<HookStorage> map(List<HookStorageSlot> slots);

    @Mapping(target = "key", source = "key")
    @Mapping(target = "value", source = "value")
    @Mapping(target = "timestamp", source = "timestampNanos", qualifiedByName = CommonMapper.QUALIFIER_TIMESTAMP)
    HookStorage map(HookStorageSlot slot);
}
