// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.convertRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.Order;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@CustomLog
@Data
@RequiredArgsConstructor
public class NetworkFeature {

    private final MirrorNodeClient mirrorClient;

    @When("I verify the network stake")
    public void verifyNetworkStake() {
        if (!shouldHaveStake()) {
            log.warn("Skipping network stake verification since there's not yet any staking information");
            return;
        }

        var networkStake = mirrorClient.getNetworkStake();
        assertThat(networkStake).isNotNull();
        assertThat(networkStake.getMaxStakeRewarded()).isNotNegative();
        assertThat(networkStake.getMaxStakingRewardRatePerHbar()).isNotNegative();
        assertThat(networkStake.getMaxTotalReward()).isNotNegative();
        assertThat(networkStake.getNodeRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getReservedStakingRewards()).isNotNegative();
        assertThat(networkStake.getRewardBalanceThreshold()).isNotNegative();
        assertThat(networkStake.getStakeTotal()).isNotNegative();
        assertNotNull(networkStake.getStakingPeriod());
        assertThat(networkStake.getStakingPeriodDuration()).isPositive();
        assertThat(networkStake.getStakingPeriodsStored()).isPositive();
        assertThat(networkStake.getStakingRewardFeeFraction()).isBetween(0.0F, 1.0F);
        assertThat(networkStake.getStakingRewardRate()).isNotNegative();
        assertThat(networkStake.getStakingStartThreshold()).isPositive();
        assertThat(networkStake.getUnreservedStakingRewardBalance()).isNotNegative();
    }

    private boolean shouldHaveStake() {
        var blocks = mirrorClient.getBlocks(Order.ASC, 1);
        if (blocks.getBlocks().isEmpty()) {
            return false;
        }

        var block = blocks.getBlocks().getFirst();
        var timestamp = convertRange(block.getTimestamp());
        if (timestamp == null || !timestamp.hasLowerBound()) {
            return false;
        }

        var midnight =
                LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return timestamp.lowerEndpoint().isBefore(midnight);
    }
}
