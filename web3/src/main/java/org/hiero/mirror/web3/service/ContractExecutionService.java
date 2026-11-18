// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Objects;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionResult;
import org.hiero.mirror.web3.service.utils.BinaryGasEstimator;

@CustomLog
@Named
public class ContractExecutionService extends ContractCallService {

    private final BinaryGasEstimator binaryGasEstimator;

    public ContractExecutionService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            RecordFileService recordFileService,
            EvmProperties evmProperties,
            TransactionExecutionService transactionExecutionService) {
        super(meterRegistry, recordFileService, evmProperties, transactionExecutionService);
        this.binaryGasEstimator = binaryGasEstimator;
    }

    /** Runs the call without reporting gas usage, for callers that did not reserve throttle gas. */
    @VisibleForTesting
    public String processCall(final ContractExecutionParameters params) {
        return processCall(params, GasUsageListener.NONE);
    }

    /** Returns only the result hex string. */
    public String processCall(final ContractExecutionParameters params, final GasUsageListener gasUsageListener) {
        return processCallWithGas(params, gasUsageListener).result();
    }

    /**
     * Returns both the result and the gas used. {@code gasUsageListener} is told about the executed call, or the first
     * call of a gas estimate, so the caller can return the throttle gas it did not use.
     */
    public ContractExecutionResult processCallWithGas(
            final ContractExecutionParameters params, final GasUsageListener gasUsageListener) {
        return ContractCallContext.run(ctx -> {
            ctx.setApi(CALL);
            ctx.setGasUsageListener(gasUsageListener);
            var stopwatch = Stopwatch.createStarted();
            var stringResult = "";
            long gasUsed;

            try {
                updateGasLimitMetric(params);

                ctx.applyStateOverrides(params.getStateOverrides());

                Bytes result;
                if (params.isEstimate()) {
                    result = estimateGas(params, ctx);
                    gasUsed = result.toLong();
                } else {
                    final var ethCallTxnResult = callContract(params, ctx);
                    result = Objects.requireNonNullElse(
                            Bytes.fromHexString(ethCallTxnResult.contractCallResult()), Bytes.EMPTY);
                    gasUsed = ethCallTxnResult.gasUsed();
                }

                stringResult = result.toHexString();
            } finally {
                log.debug("Processed request {} in {}: {}", params, stopwatch, stringResult);
            }

            return new ContractExecutionResult(stringResult, gasUsed);
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
     * gas used in the first step, while the upper bound is the inputted gas parameter. A 5% safety margin is added
     * to the successful limit, independently of storage refunds.
     */
    private Bytes estimateGas(final ContractExecutionParameters params, final ContractCallContext context) {
        final var processingResult = callContract(params, context);
        final var gasUsedByInitialCall = processingResult.gasUsed();

        final var status = ResponseCodeEnum.SUCCESS.toString();
        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateMetrics(params, totalGas, iterations, status),
                gas -> doProcessCall(params, gas, true),
                gasUsedByInitialCall,
                params.getGas());

        if (estimatedGas > evmProperties.getMaxGasLimit()) {
            throw new MirrorEvmTransactionException(
                    ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED.name(),
                    "Gas estimate exceeds the maximum gas limit",
                    HEX_PREFIX,
                    processingResult);
        }

        return Bytes.ofUnsignedLong(estimatedGas);
    }
}
