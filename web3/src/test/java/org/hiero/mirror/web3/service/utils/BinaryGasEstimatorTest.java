// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.utils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class BinaryGasEstimatorTest extends Web3IntegrationTest {
    private final BinaryGasEstimator binaryGasEstimator;
    private final EvmProperties properties;
    private final AtomicInteger iterations = new AtomicInteger(0);

    @BeforeEach
    void resetIterations() {
        iterations.set(0);
    }

    /**
     * {@link BinaryGasEstimator} uses the gas consumed by the initial call as the lower bound, probes an optimistic
     * EIP-150 gas limit, and then performs a go-ethereum-style binary search. A 5% minimum headroom is applied over the
     * initial gas used before returning.
     */
    @DisplayName("binarySearch")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, iterationLimit{2}")
    @CsvSource({
        "23850, 100000, 6",
        "35000, 15_000_000, 14",
        "1_000_000, 1_000_000_000, 20",
        "21000, 15_000_000, 14",
        "21000, 50_000_000, 15",
        "1_000_000, 1_000_000_000, 20"
    })
    void binarySearch(final long low, final long high, final int iterationLimit) {
        // First call with no failing contract calls for gasUsed reference
        final var regularCall = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b), unused -> createTxnResult(low, true), low, high);

        assertThat(regularCall)
                .as("result must include at least 5%% headroom over the initial gas used")
                .isGreaterThanOrEqualTo((long) Math.ceil(low * GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE));
        assertThat(regularCall)
                .as("result must not exceed the caller gas limit when the limit can fit the 5%% headroom")
                .isLessThanOrEqualTo(high);

        assertThat(iterations.get()).as("iteration limit").isLessThanOrEqualTo(iterationLimit);
    }

    /**
     * Drives the estimator directly across a wide spread of gas magnitudes and gas limits. Unlike the dummy used by
     * {@link #binarySearch}, here the call only succeeds once it is given at least the gas the transaction actually
     * needs (mirroring a real estimation), so the search has to locate that requirement. The resulting estimate must
     * land in the accepted 5%-20% headroom over the gas actually required.
     */
    @DisplayName("estimateStaysWithinExpectedGasRange")
    @ParameterizedTest(name = "#{index} (gasRequired {0}, gasLimit {1})")
    @CsvSource({
        "21000, 1_800_000",
        "45000, 1_800_000",
        "120000, 1_800_000",
        "300000, 1_800_000",
        "607854, 1_800_000",
        "750000, 3_000_000",
        "1_000_000, 5_000_000",
        "2_500_000, 15_000_000",
        "5_000_000, 15_000_000",
        "120000, 250000",
        "480000, 1_000_000",
        "33333, 15_000_000"
    })
    void estimateStaysWithinExpectedGasRange(final long gasRequired, final long gasLimit) {
        final var estimate = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b),
                gas -> createTxnResult(gasRequired, gas >= gasRequired),
                gasRequired,
                gasLimit);

        assertThat(estimate).as("result must not go out of bounds").isBetween(gasRequired, gasLimit);
        assertThat(iterations.get())
                .as("iteration limit")
                .isLessThanOrEqualTo(properties.getMaxGasEstimateRetriesCount());
        assertThat(isWithinExpectedGasRange(estimate, gasRequired))
                .as(
                        "estimate %d must be within the accepted 5%-20% headroom over the gas required %d",
                        estimate, gasRequired)
                .isTrue();
    }

    @DisplayName("binarySearchWithFailingCalls")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, regularCallGasUsage{2}")
    @CsvSource({
        "21000, 100000, 21617",
        "35000, 15_000_000, 35913",
        "1_000_000, 1_000_000_000, 1000952",
        "21000, 15_000_000, 21914",
        "21000, 50_000_000, 21914",
        "1_000_000, 1_000_000_000, 1000952"
    })
    void binarySearchWithFailingCalls(final long low, final long high, final int regularCallGasUsage) {
        // Call where every second contract call fails
        final var callCount = new AtomicInteger(0);
        final var callResult = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b),
                unused -> createTxnResult(low, failEverySecondCall(callCount)),
                low,
                high);

        assertThat(callResult).as("result must not go out of bounds").isBetween(low, high);
        assertThat(iterations.get())
                .as("iteration limit")
                .isLessThanOrEqualTo(properties.getMaxGasEstimateRetriesCount());

        // asserting that every revert while executing is treated like INSUFFICIENT_GAS
        assertThat(callResult)
                .as("estimated gas in search containing failed calls transactions is higher than the one with "
                        + "only successful transactions")
                .isGreaterThan(regularCallGasUsage);
    }

    @Test
    void searchDoesntExceedMaxIterations() {
        /*
         * With such values for low and high, iterations would over the maximum of 20, we test the
         * whether the condition in BinaryGasEstimator keeps the iteration under this 20 threshold.
         */
        final var low = 0;
        final var high = Long.MAX_VALUE;
        binaryGasEstimator.search((a, b) -> iterations.addAndGet(b), unused -> createTxnResult(0, false), low, high);

        assertThat(iterations.get())
                .as("iteration limit")
                .isLessThanOrEqualTo(properties.getMaxGasEstimateRetriesCount());
    }

    private EvmTransactionResult createTxnResult(final long gasUsed, final boolean isSuccessful) {
        if (!isSuccessful) {
            return new EvmTransactionResult(
                    ResponseCodeEnum.FAIL_INVALID,
                    ContractFunctionResult.newBuilder().gasUsed(gasUsed).build());
        }
        return new EvmTransactionResult(
                ResponseCodeEnum.SUCCESS,
                ContractFunctionResult.newBuilder().gasUsed(gasUsed).build());
    }

    private boolean failEverySecondCall(final AtomicInteger callCount) {
        return callCount.incrementAndGet() % 2 != 0;
    }
}
