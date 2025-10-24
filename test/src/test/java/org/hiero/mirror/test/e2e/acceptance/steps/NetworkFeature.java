// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.convertRange;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.springframework.context.annotation.Scope;

@CustomLog
@Data
@RequiredArgsConstructor
@Scope("cucumber-glue")
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

    @When("I verify the network fees")
    public void verifyNetworkFee() {
        if (mirrorClient.hasPartialState()) {
            log.warn("Skipping network fees verification in case of partial state");
            return;
        }
        final var networkFees = mirrorClient.getNetworkFees();
        assertThat(networkFees)
                .isNotNull()
                .satisfies(f -> assertThat(f.getTimestamp()).isNotNull())
                .extracting(NetworkFeesResponse::getFees, InstanceOfAssertFactories.list(NetworkFee.class))
                .hasSize(3)
                .allSatisfy(fee -> {
                    assertThat(fee.getTransactionType()).isIn("ContractCall", "ContractCreate", "EthereumTransaction");
                    assertThat(fee.getGas()).isGreaterThan(0);
                });
    }

    @When("I verify the network supply")
    public void verifyNetworkSupply() {
        if (mirrorClient.hasPartialState()) {
            log.warn("Skipping network fees verification in case of partial state");
            return;
        }
        final var networkSupply = mirrorClient.getNetworkSupply();
        assertThat(networkSupply).isNotNull();

        final var totalSupply = parseToBigDecimal(networkSupply.getTotalSupply());
        final var releasedSupply = parseToBigDecimal(networkSupply.getReleasedSupply());

        assertThat(totalSupply).isGreaterThan(BigDecimal.ZERO);
        assertThat(releasedSupply).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(releasedSupply).isLessThan(totalSupply);
        assertThat(networkSupply.getTimestamp()).isNotNull();
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

    private BigDecimal parseToBigDecimal(final String number) {
        assertThat(number).isNotNull();
        assertThatCode(() -> new BigDecimal(number)).doesNotThrowAnyException();
        return new BigDecimal(number);
    }
}
