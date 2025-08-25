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
import java.util.Set;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.Order;

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

    @When("I verify the network fees")
    public void verifyNetworkFee() {
        var networkFees = mirrorClient.getNetworkFees();
        assertThat(networkFees).isNotNull();
        assertThat(networkFees.getFees()).isNotEmpty();

        final var fees = networkFees.getFees();
        assertThat(fees.size()).isEqualTo(3);

        var expectedTypes = Set.of("ContractCall", "ContractCreate", "EthereumTransaction");
        var actualTypes = fees.stream().map(NetworkFee::getTransactionType).collect(Collectors.toSet());
        assertThat(actualTypes).containsAll(expectedTypes);

        fees.forEach(fee -> {
            assertThat(fee.getGas()).isGreaterThan(0);
        });

        assertThat(networkFees.getTimestamp()).isNotNull();
    }

    @When("I verify the network supply")
    public void verifyNetworkSupply() {
        var networkSupply = mirrorClient.getNetworkSupply();
        assertThat(networkSupply).isNotNull();

        var totalSupply = mustParseToBigDecimal(networkSupply.getTotalSupply());
        var releasedSupply = mustParseToBigDecimal(networkSupply.getReleasedSupply());

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

    private BigDecimal mustParseToBigDecimal(final String number) {
        assertThat(number).isNotNull();
        assertThatCode(() -> new BigDecimal(number)).doesNotThrowAnyException();
        return new BigDecimal(number);
    }
}
