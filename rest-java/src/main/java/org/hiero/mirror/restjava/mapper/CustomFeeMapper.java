// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.rest.model.ConsensusCustomFees;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = FixedCustomFeeMapper.class)
public interface CustomFeeMapper {

    @Mapping(source = "timestampRange", target = "createdTimestamp")
    ConsensusCustomFees map(CustomFee customFee);
}
