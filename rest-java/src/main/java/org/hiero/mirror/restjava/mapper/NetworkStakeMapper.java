// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP_RANGE;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface NetworkStakeMapper {

    @Mapping(source = "stakingPeriod", target = "stakingPeriod", qualifiedByName = QUALIFIER_TIMESTAMP_RANGE)
    @Mapping(
            target = "nodeRewardFeeFraction",
            expression =
                    "java(commonMapper.mapFraction(source.getNodeRewardFeeNumerator(), source.getNodeRewardFeeDenominator()))")
    @Mapping(
            target = "stakingRewardFeeFraction",
            expression =
                    "java(commonMapper.mapFraction(source.getStakingRewardFeeNumerator(), source.getStakingRewardFeeDenominator()))")
    NetworkStakeResponse map(NetworkStake source);
}
