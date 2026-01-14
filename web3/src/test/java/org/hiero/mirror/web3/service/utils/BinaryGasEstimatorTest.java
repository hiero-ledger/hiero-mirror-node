// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.utils;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.assertj.core.data.Percentage;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class BinaryGasEstimatorTest extends Web3IntegrationTest {
    private final BinaryGasEstimator binaryGasEstimator;
    private final MirrorNodeEvmProperties properties;
    private final AtomicInteger iterations = new AtomicInteger(0);

    /**
     * @link BinaryGasEstimator is using slightly modified binary algorithm which is coupled to some exttend with the
     * gas used estimation and its metric updates. Here the logic of processing contact calls is replaced with a dummy
     * function, returning successful/failed HederaEvmTransactionProcessingResult with gasUsed value equal to the low
     * value, which corresponds to the intial gasUsed in the real world. The metric logic is replaced with a lambda
     * which counts the iterations count.
     */
    @DisplayName("binarySearch")
    @ParameterizedTest(name = "#{index} (low {0}, high {1}, iterationLimit{2}")
    @CsvSource({
        "23850, 100000, 6",
        "35000, 15_000_000, 14",
        "55555, 55555, 1",
        "77777, 77778, 1",
        "1_000_000, 1_000_000_000, 20",
        "21000, 15_000_000, 14",
        "21000, 50_000_000, 15",
        "1_000_000, 1_000_000_000, 20"
    })
    void binarySearch(final long low, final long high, final int iterationLimit) {
        // First call with no failing contract calls for gasUsed reference
        final var regularCall = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b), unused -> createTxnResult(low, true), low, high);

        assertThat(regularCall).as("result must not go out of bounds").isBetween(low, high);

        assertThat(regularCall)
                .as("result must be within the 20% range of the initial gasUsed(low param)")
                .isCloseTo(low, Percentage.withPercentage(20));

        assertThat(iterations.get()).as("iteration limit").isLessThanOrEqualTo(iterationLimit);
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
        final var callResult = binaryGasEstimator.search(
                (a, b) -> iterations.addAndGet(b), unused -> createTxnResult(low, failEverySecondCall()), low, high);

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

    private boolean failEverySecondCall() {
        return iterations.get() > 0 && iterations.get() % 2 == 0;
    }
}
