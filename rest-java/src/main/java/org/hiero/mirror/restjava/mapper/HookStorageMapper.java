// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.hook.HookStorage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface HookStorageMapper extends CollectionMapper<HookStorage, org.hiero.mirror.rest.model.HookStorage> {
    @Mapping(source = "modifiedTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    org.hiero.mirror.rest.model.HookStorage map(HookStorage source);
}
