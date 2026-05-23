// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.state.Utils.normalizeStorageSlot;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionResult;
import org.hiero.mirror.web3.service.utils.BinaryGasEstimator;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.NormalizedStateOverride;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;

@CustomLog
@Named
public class ContractExecutionService extends ContractCallService {

    private final BinaryGasEstimator binaryGasEstimator;

    @SuppressWarnings("java:S107")
    public ContractExecutionService(
            MeterRegistry meterRegistry,
            BinaryGasEstimator binaryGasEstimator,
            RecordFileService recordFileService,
            ThrottleProperties throttleProperties,
            ThrottleManager throttleManager,
            EvmProperties evmProperties,
            TransactionExecutionService transactionExecutionService) {
        super(
                throttleManager,
                throttleProperties,
                meterRegistry,
                recordFileService,
                evmProperties,
                transactionExecutionService);
        this.binaryGasEstimator = binaryGasEstimator;
    }

    /**
     * Backwards compatible method returning only the result hex string.
     */
    public String processCall(final ContractExecutionParameters params) {
        return processCallWithGas(params).result();
    }

    /**
     * New API that returns both the result and the actual gas used by the execution.
     */
    public ContractExecutionResult processCallWithGas(final ContractExecutionParameters params) {
        return ContractCallContext.run(ctx -> {
            var stopwatch = Stopwatch.createStarted();
            var stringResult = "";
            long gasUsed;

            try {
                updateGasLimitMetric(params);

                if (params.getStateOverrides() != null
                        && !params.getStateOverrides().isEmpty()) {
                    ctx.setStateOverrides(normalizeOverrides(params.getStateOverrides()));
                }

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
     * Normalizes a list of state overrides into a map keyed by lowercase {@code 0x}-prefixed EVM address.
     * Slot keys within each override are normalized to 64-character hex for direct map access in the state layer.
     */
    private static Map<String, NormalizedStateOverride> normalizeOverrides(List<StateOverride> overrides) {
        var result = new HashMap<String, NormalizedStateOverride>(overrides.size());
        for (var override : overrides) {
            var normalized = NormalizedStateOverride.builder()
                    .balance(override.getBalance())
                    .nonce(override.getNonce())
                    .code(override.getCode())
                    .state(override.getState() != null ? normalizeSlotList(override.getState()) : null)
                    .stateDiff(override.getStateDiff() != null ? normalizeSlotList(override.getStateDiff()) : null)
                    .build();
            result.put(normalizeAddress(override.getAddress()), normalized);
        }
        return result;
    }

    private static Map<String, String> normalizeSlotList(List<StorageEntry> entries) {
        var result = new HashMap<String, String>(entries.size());
        for (var entry : entries) {
            result.put(normalizeStorageSlot(entry.getKey()), entry.getValue());
        }
        return result;
    }

    static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        var hex = address.startsWith(HEX_PREFIX) || address.startsWith("0X") ? address.substring(2) : address;
        return HEX_PREFIX + hex.toLowerCase();
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
        final var gasUsedByInitialCall = processingResult.gasUsed();

        // sanity check ensuring gasUsed is always lower than the inputted one
        if (gasUsedByInitialCall >= params.getGas()) {
            return Bytes.ofUnsignedLong(gasUsedByInitialCall);
        }

        final var status = ResponseCodeEnum.SUCCESS.toString();
        final var estimatedGas = binaryGasEstimator.search(
                (totalGas, iterations) -> updateMetrics(params, totalGas, iterations, status),
                gas -> doProcessCall(params, gas, true),
                gasUsedByInitialCall,
                params.getGas());

        return Bytes.ofUnsignedLong(estimatedGas);
    }
}
