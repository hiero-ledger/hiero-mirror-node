// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.util.NetworkStakeUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, imports = NetworkStakeUtils.class)
public interface NetworkStakeMapper {

    @Mapping(
            target = "stakingPeriod",
            expression = "java(NetworkStakeUtils.toTimestampRange(source.getStakingPeriod()))")
    @Mapping(
            target = "nodeRewardFeeFraction",
            expression =
                    "java(NetworkStakeUtils.toFraction(source.getNodeRewardFeeNumerator(), source.getNodeRewardFeeDenominator()))")
    @Mapping(
            target = "stakingRewardFeeFraction",
            expression =
                    "java(NetworkStakeUtils.toFraction(source.getStakingRewardFeeNumerator(), source.getStakingRewardFeeDenominator()))")
    NetworkStakeResponse map(NetworkStake source);
}
