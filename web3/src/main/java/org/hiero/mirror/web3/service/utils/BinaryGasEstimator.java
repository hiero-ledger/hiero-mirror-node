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
    private final EvmProperties properties;

    public long search(
            final ObjIntConsumer<Long> metricUpdater, final LongFunction<EvmTransactionResult> call, long lo, long hi) {
        long prevGasLimit = lo;
        int iterationsMade = 0;
        long totalGasUsed = 0;

        // Limit search precision separately from the safety margin applied to the successful upper bound below.
        final long estimateIterationThreshold = (long) (lo * properties.getEstimateGasIterationThresholdPercent());

        final var contractCallContext = ContractCallContext.get();
        while (lo < hi - 1 && iterationsMade < properties.getMaxGasEstimateRetriesCount()) {
            contractCallContext.reset();

            final long mid = lo + (hi - lo) / 2;

            // If modularizedServices is true - we call the safeCall function that handles if an exception is thrown
            final var transactionResult = safeCall(mid, call);

            iterationsMade++;

            final boolean err =
                    transactionResult == null || !transactionResult.isSuccessful() || transactionResult.gasUsed() < 0;
            final long gasUsed = err ? prevGasLimit : transactionResult.gasUsed();
            totalGasUsed += gasUsed;
            if (err || gasUsed == 0) {
                lo = mid;
            } else {
                hi = mid;
                if (Math.abs(prevGasLimit - mid) < estimateIterationThreshold) {
                    lo = hi;
                }
            }
            prevGasLimit = mid;
        }

        metricUpdater.accept(totalGasUsed, iterationsMade);

        // Refunds reduce gasUsed, not the gas limit required during execution. Pad the successful limit instead.
        // Add ceil(hi * 5%) with integer arithmetic and reject overflow rather than returning a smaller limit.
        return Math.addExact(hi, hi / 20 + (hi % 20 == 0 ? 0 : 1));
    }

    // This method is needed because within the modularized services if the contract call fails an exception is thrown
    // instead of transaction result with 'failed' status which will result in a failing test. This way we handle the
    // exception and return estimated gas
    private EvmTransactionResult safeCall(long mid, LongFunction<EvmTransactionResult> call) {
        try {
            return call.apply(mid);
        } catch (Exception ignored) {
            log.info("Exception while calling contract for gas estimation");
            return null;
        }
    }
}
