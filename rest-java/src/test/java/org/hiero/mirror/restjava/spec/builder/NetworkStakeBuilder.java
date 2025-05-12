// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class NetworkStakeBuilder extends AbstractEntityBuilder<NetworkStake, NetworkStake.NetworkStakeBuilder> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::networkStakes;
    }

    @Override
    protected NetworkStake.NetworkStakeBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return NetworkStake.builder()
                .consensusTimestamp(0L)
                .epochDay(0L)
                .maxStakeRewarded(10L)
                .maxStakingRewardRatePerHbar(17808L)
                .maxTotalReward(20L)
                .nodeRewardFeeDenominator(0L)
                .nodeRewardFeeNumerator(100L)
                .reservedStakingRewards(30L)
                .rewardBalanceThreshold(40L)
                .stakeTotal(10_000_000L)
                .stakingPeriod(86_400_000_000_000L - 1L)
                .stakingPeriodDuration(1440L)
                .stakingPeriodsStored(365L)
                .stakingRewardFeeDenominator(100L)
                .stakingRewardFeeNumerator(100L)
                .stakingRewardRate(100_000_000_000L)
                .stakingStartThreshold(25_000_000_000_000_000L)
                .unreservedStakingRewardBalance(50L);
    }

    @Override
    protected NetworkStake getFinalEntity(NetworkStake.NetworkStakeBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
