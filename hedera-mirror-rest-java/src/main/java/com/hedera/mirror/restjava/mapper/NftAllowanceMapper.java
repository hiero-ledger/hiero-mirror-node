// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface NftAllowanceMapper extends CollectionMapper<NftAllowance, com.hedera.mirror.rest.model.NftAllowance> {

    @Mapping(source = "timestampRange", target = "timestamp")
    com.hedera.mirror.rest.model.NftAllowance map(NftAllowance source);
}
