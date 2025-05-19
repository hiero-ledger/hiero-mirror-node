// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface NftAllowanceMapper extends CollectionMapper<NftAllowance, org.hiero.mirror.rest.model.NftAllowance> {

    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.NftAllowance map(NftAllowance source);
}
