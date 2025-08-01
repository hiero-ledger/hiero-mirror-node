// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class NetworkStakeMapperTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private CommonMapper commonMapper;
    private NetworkStakeMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new NetworkStakeMapperImpl(commonMapper);
    }

    @Test
    void map() {
        // given
        final var stake = domainBuilder
                .networkStake()
                .customize(n -> n.stakingRewardFeeNumerator(10L))
                .get();

        final float expectedNodeRewardFeeFraction =
                commonMapper.mapFraction(stake.getNodeRewardFeeNumerator(), stake.getNodeRewardFeeDenominator());
        final float expectedStakingRewardFeeFraction =
                commonMapper.mapFraction(stake.getStakingRewardFeeNumerator(), stake.getStakingRewardFeeDenominator());
        final var expectedRange = commonMapper.mapTimestampRange(stake.getStakingPeriod());

        // when
        final var response = mapper.map(stake);

        // then
        assertThat(response)
                .returns(stake.getMaxStakeRewarded(), NetworkStakeResponse::getMaxStakeRewarded)
                .returns(stake.getMaxStakingRewardRatePerHbar(), NetworkStakeResponse::getMaxStakingRewardRatePerHbar)
                .returns(stake.getMaxTotalReward(), NetworkStakeResponse::getMaxTotalReward)
                .returns(expectedNodeRewardFeeFraction, NetworkStakeResponse::getNodeRewardFeeFraction)
                .returns(stake.getReservedStakingRewards(), NetworkStakeResponse::getReservedStakingRewards)
                .returns(stake.getRewardBalanceThreshold(), NetworkStakeResponse::getRewardBalanceThreshold)
                .returns(stake.getStakeTotal(), NetworkStakeResponse::getStakeTotal)
                .returns(stake.getStakingPeriodDuration(), NetworkStakeResponse::getStakingPeriodDuration)
                .returns(stake.getStakingPeriodsStored(), NetworkStakeResponse::getStakingPeriodsStored)
                .returns(expectedStakingRewardFeeFraction, NetworkStakeResponse::getStakingRewardFeeFraction)
                .returns(stake.getStakingRewardRate(), NetworkStakeResponse::getStakingRewardRate)
                .returns(stake.getStakingStartThreshold(), NetworkStakeResponse::getStakingStartThreshold)
                .returns(
                        stake.getUnreservedStakingRewardBalance(),
                        NetworkStakeResponse::getUnreservedStakingRewardBalance)
                .returns(expectedRange, NetworkStakeResponse::getStakingPeriod);
    }

    @Test
    void mapEmpty() {
        final var stake = new NetworkStake();
        final var response = mapper.map(stake);

        assertThat(response)
                .returns(0L, NetworkStakeResponse::getMaxStakeRewarded)
                .returns(0L, NetworkStakeResponse::getMaxStakingRewardRatePerHbar)
                .returns(0L, NetworkStakeResponse::getMaxTotalReward)
                .returns(0.0f, NetworkStakeResponse::getNodeRewardFeeFraction)
                .returns(0L, NetworkStakeResponse::getReservedStakingRewards)
                .returns(0L, NetworkStakeResponse::getRewardBalanceThreshold)
                .returns(0L, NetworkStakeResponse::getStakeTotal)
                .returns(0L, NetworkStakeResponse::getStakingPeriodDuration)
                .returns(0L, NetworkStakeResponse::getStakingPeriodsStored)
                .returns(0.0f, NetworkStakeResponse::getStakingRewardFeeFraction)
                .returns(0L, NetworkStakeResponse::getStakingRewardRate)
                .returns(0L, NetworkStakeResponse::getStakingStartThreshold)
                .returns(0L, NetworkStakeResponse::getUnreservedStakingRewardBalance);

        var expectedStakingPeriod = commonMapper.mapTimestampRange(stake.getStakingPeriod());
        assertThat(response.getStakingPeriod()).isEqualTo(expectedStakingPeriod);
    }
}
