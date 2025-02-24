// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.rest.model.ConsensusCustomFees;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = FixedCustomFeeMapper.class)
public interface CustomFeeMapper {

    @Mapping(source = "timestampRange", target = "createdTimestamp")
    ConsensusCustomFees map(CustomFee customFee);
}
