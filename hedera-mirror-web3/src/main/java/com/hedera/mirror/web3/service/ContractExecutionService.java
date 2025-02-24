// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Objects;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;

@CustomLog
@Named
public class ContractExecutionService extends ContractCallService {

    private final BinaryGasEstimator binaryGasEstimator;

    @SuppressWarnings("java:S107")
    public ContractExecutionService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            RecordFileService recordFileService,
            ThrottleProperties throttleProperties,
            Bucket gasLimitBucket,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            TransactionExecutionService transactionExecutionService) {
        super(
                mirrorEvmTxProcessor,
                gasLimitBucket,
                throttleProperties,
                meterRegistry,
                recordFileService,
                store,
                mirrorNodeEvmProperties,
                transactionExecutionService);
        this.binaryGasEstimator = binaryGasEstimator;
    }

    public String processCall(final ContractExecutionParameters params) {
        return ContractCallContext.run(ctx -> {
            var stopwatch = Stopwatch.createStarted();
            var stringResult = "";

            try {
                updateGasLimitMetric(params.getCallType(), params.getGas());

                Bytes result;
                if (params.isEstimate()) {
                    result = estimateGas(params, ctx);
                } else {
                    final var ethCallTxnResult = callContract(params, ctx);
                    result = Objects.requireNonNullElse(ethCallTxnResult.getOutput(), Bytes.EMPTY);
                }

                stringResult = result.toHexString();
            } finally {
                log.debug("Processed request {} in {}: {}", params, stopwatch, stringResult);
            }

            return stringResult;
        });
    }

    /**
     * This method estimates the amount of gas required to execute a smart contract function. The estimation process
     * involves two steps:
     * <p>
     * 1. Firstly, a call is made with user inputted gas value (default and maximum value for this parameter is 15
     * million) to determine if the call estimation is possible. This step is intended to quickly identify any issues
     * that would prevent the estimation from succeeding.
     * <p>
     * 2. Finally, if the first step is successful, a binary search is initiated. The lower bound of the search is the
     * gas used in the first step, while the upper bound is the inputted gas parameter.
     */
    private Bytes estimateGas(final ContractExecutionParameters params, final ContractCallContext context) {
        final var processingResult = callContract(params, context);
        validateResult(processingResult, CallType.ETH_ESTIMATE_GAS);

        final var gasUsedByInitialCall = processingResult.getGasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateGasUsedMetric(CallType.ETH_ESTIMATE_GAS, totalGas, iterations),
                gas -> doProcessCall(params, gas, false),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }
}
