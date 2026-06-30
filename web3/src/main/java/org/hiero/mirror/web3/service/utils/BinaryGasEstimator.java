// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.utils;

import jakarta.inject.Named;
import java.util.function.LongFunction;
import java.util.function.ObjIntConsumer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;

@CustomLog
@RequiredArgsConstructor
@Named
public class BinaryGasEstimator {

    // EIP-150 call stipend used by ethereum's optimistic gas limit calculation.
    private static final long CALL_STIPEND = 2_300L;

    private final EvmProperties properties;

    /**
     * Binary search for the smallest gas limit that allows the transaction to execute successfully.
     *
     * @param metricUpdater   callback to record total gas used and iteration count
     * @param call            function that executes the transaction at a given gas limit
     * @param initialGasUsed  gas consumed by the initial unconstrained execution at the upper bound
     * @param hi              upper bound for the search (caller gas limit)
     * @return estimated gas limit
     */
    public long search(
            final ObjIntConsumer<Long> metricUpdater,
            final LongFunction<EvmTransactionResult> call,
            long initialGasUsed,
            long hi) {
        var lo = Math.max(0, initialGasUsed - 1);
        var iterationsMade = 0;
        var totalGasUsed = 0L;

        final var errorRatio = properties.getEstimateGasIterationThresholdPercent();
        final var maxIterations = properties.getMaxGasEstimateRetriesCount();
        final var contractCallContext = ContractCallContext.get();

        // Optimistic gas limit: accounts for gas refunds and the 63/64 call gas forwarding rule.
        final var optimisticGasLimit = (initialGasUsed + CALL_STIPEND) * 64 / 63;
        if (optimisticGasLimit < hi) {
            contractCallContext.reset();
            final var result = safeCall(optimisticGasLimit, call);
            iterationsMade++;
            totalGasUsed += gasUsedOrLimit(result, optimisticGasLimit);

            if (isGasRelatedFailure(result)) {
                lo = optimisticGasLimit;
            } else {
                hi = optimisticGasLimit;
            }
        }

        while (lo + 1 < hi && iterationsMade < maxIterations) {
            // lo = highest gas limit known to fail (too little gas)
            // hi = lowest gas limit known to succeed (the current best answer, which is what gets returned)
            // hi - lo = the width of the remaining uncertainty window
            // (hi - lo) / hi = that window expressed as a fraction of the current answer (the relative width)
            if (errorRatio > 0 && (double) (hi - lo) / hi < errorRatio) {
                break;
            }

            contractCallContext.reset();

            var mid = lo + (hi - lo) / 2;
            if (mid > lo * 2) {
                // Skew bisection toward the low side; most txs need only slightly more gas than used.
                mid = lo * 2;
            }

            final var result = safeCall(mid, call);
            iterationsMade++;
            totalGasUsed += gasUsedOrLimit(result, mid);

            if (isGasRelatedFailure(result)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        metricUpdater.accept(totalGasUsed, iterationsMade);
        return Math.max(hi, (long) Math.ceil(initialGasUsed * 1.05));
    }

    private static long gasUsedOrLimit(final EvmTransactionResult result, final long gasLimit) {
        return result != null && result.gasUsed() >= 0 ? result.gasUsed() : gasLimit;
    }

    private static boolean isGasRelatedFailure(final EvmTransactionResult result) {
        return result == null || !result.isSuccessful() || result.gasUsed() < 0;
    }

    private EvmTransactionResult safeCall(final long gasLimit, final LongFunction<EvmTransactionResult> call) {
        try {
            return call.apply(gasLimit);
        } catch (Exception ignored) {
            log.info("Exception while calling contract for gas estimation");
            return null;
        }
    }
}
