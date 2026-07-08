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

        // Now that we also support gas estimates for precompile calls, the default threshold is too low, since
        // it does not take into account the minimum threshold of 5% higher estimate than the actual gas used.
        // The default value is working with some calls but that is not the case for precompile calls which have higher
        // gas consumption.
        // Configurable tolerance of 10% over 5% is used, since the algorithm fails when using 5%, producing too narrow
        // threshold. Adjust via estimateGasIterationThresholdPercent value.
        final long estimateIterationThreshold = (long) (lo * properties.getEstimateGasIterationThresholdPercent());

        ContractCallContext contractCallContext = ContractCallContext.get();
        while (lo + 1 < hi && iterationsMade < properties.getMaxGasEstimateRetriesCount()) {
            contractCallContext.reset();

            long mid = (hi + lo) / 2;

            // If modularizedServices is true - we call the safeCall function that handles if an exception is thrown
            final var transactionResult = safeCall(mid, call);

            iterationsMade++;

            boolean err =
                    transactionResult == null || !transactionResult.isSuccessful() || transactionResult.gasUsed() < 0;
            long gasUsed = err ? prevGasLimit : transactionResult.gasUsed();
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
        return hi;
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
