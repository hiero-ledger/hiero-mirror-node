// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.apache.logging.log4j.util.Strings.EMPTY;
import static org.hiero.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static org.hiero.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;

@Named
@CustomLog
public abstract class ContractCallService {

    static final String EVM_INVOCATION_METRIC = "hiero.mirror.web3.evm.invocation";
    static final String GAS_LIMIT_METRIC = "hiero.mirror.web3.evm.gas.limit";
    static final String GAS_USED_METRIC = "hiero.mirror.web3.evm.gas.used";

    protected final Store store;
    protected final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private final MeterProvider<Counter> invocationCounter;
    private final MeterProvider<Counter> gasLimitCounter;
    private final MeterProvider<Counter> gasUsedCounter;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileService recordFileService;
    private final ThrottleProperties throttleProperties;
    private final Bucket gasLimitBucket;
    private final TransactionExecutionService transactionExecutionService;

    @SuppressWarnings("java:S107")
    protected ContractCallService(
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            Store store,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            TransactionExecutionService transactionExecutionService) {
        this.invocationCounter = Counter.builder(EVM_INVOCATION_METRIC)
                .description("The number of EVM invocations")
                .withRegistry(meterRegistry);
        this.gasLimitCounter = Counter.builder(GAS_LIMIT_METRIC)
                .description("The amount of gas limit sent in the request")
                .withRegistry(meterRegistry);
        this.gasUsedCounter = Counter.builder(GAS_USED_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.store = store;
        this.mirrorEvmTxProcessor = mirrorEvmTxProcessor;
        this.recordFileService = recordFileService;
        this.throttleProperties = throttleProperties;
        this.gasLimitBucket = gasLimitBucket;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.transactionExecutionService = transactionExecutionService;
    }

    @VisibleForTesting
    public HederaEvmTransactionProcessingResult callContract(CallServiceParameters params)
            throws MirrorEvmTransactionException {
        return ContractCallContext.run(context -> callContract(params, context));
    }

    /**
     * This method is responsible for calling a smart contract function. The method is divided into two main parts:
     * <p>
     * 1. If the call is historical, the method retrieves the corresponding record file and initializes the contract
     * call context with the historical state. The method then proceeds to call the contract.
     * </p>
     * <p>
     * 2. If the call is not historical, the method initializes the contract call context with the current state and
     * proceeds to call the contract.
     * </p>
     *
     * @param params the call service parameters
     * @param ctx    the contract call context
     * @return {@link HederaEvmTransactionProcessingResult} of the contract call
     * @throws MirrorEvmTransactionException if any pre-checks fail with {@link IllegalStateException} or
     *                                       {@link IllegalArgumentException}
     */
    protected final HederaEvmTransactionProcessingResult callContract(
            CallServiceParameters params, ContractCallContext ctx) throws MirrorEvmTransactionException {
        ctx.setCallServiceParameters(params);

        if (params.isModularized() || params.getBlock() != BlockType.LATEST) {
            ctx.setRecordFile(recordFileService
                    .findByBlockType(params.getBlock())
                    .orElseThrow(BlockNumberNotFoundException::new));
        }

        // initializes the stack frame with the current state or historical state (if the call is historical)
        if (!params.isModularized()) {
            ctx.initializeStackFrames(store.getStackedStateFrames());
        }

        return doProcessCall(params, params.getGas(), false);
    }

    protected final HederaEvmTransactionProcessingResult doProcessCall(
            CallServiceParameters params, long estimatedGas, boolean estimate) throws MirrorEvmTransactionException {
        HederaEvmTransactionProcessingResult result = null;
        var status = ResponseCodeEnum.SUCCESS.toString();

        try {
            if (params.isModularized()) {
                result = transactionExecutionService.execute(params, estimatedGas);
            } else {
                result = mirrorEvmTxProcessor.execute(params, estimatedGas);
            }

            if (!estimate) {
                validateResult(result, params);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY, EMPTY, params.isModularized());
        } catch (MirrorEvmTransactionException e) {
            // This result is needed in case of exception to be still able to call restoreGasToBucket method
            result = e.getResult();
            status = e.getMessage();
            throw e;
        } finally {
            if (!estimate) {
                restoreGasToBucket(result, params.getGas());

                // Only record metric if EVM is invoked and not inside estimate loop
                if (result != null) {
                    updateMetrics(params, result.getGasUsed(), 1, status);
                }
            }
        }
        return result;
    }

    private void restoreGasToBucket(HederaEvmTransactionProcessingResult result, long gasLimit) {
        final var gasUnit = throttleProperties.getGasUnit();
        // If the transaction fails, gasUsed is equal to gasLimit, so restore the configured refund percent
        // of the gasLimit value back in the bucket.
        final var gasLimitToRestoreBaseline =
                (long) (Math.floorDiv(gasLimit, gasUnit) * throttleProperties.getGasLimitRefundPercent() / 100f);
        if (result == null || (!result.isSuccessful() && gasLimit == result.getGasUsed())) {
            gasLimitBucket.addTokens(gasLimitToRestoreBaseline);
        } else {
            // The transaction was successful or reverted, so restore the remaining gas back in the bucket or
            // the configured refund percent of the gasLimit value back in the bucket - whichever is lower.
            final var gasRemaining = gasLimit - result.getGasUsed();
            gasLimitBucket.addTokens(Math.min(Math.floorDiv(gasRemaining, gasUnit), gasLimitToRestoreBaseline));
        }
    }

    protected void validateResult(
            final HederaEvmTransactionProcessingResult txnResult, final CallServiceParameters params) {
        if (!txnResult.isSuccessful()) {
            var revertReason = txnResult.getRevertReason().orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReason);
            var status = getStatusOrDefault(txnResult).name();
            throw new MirrorEvmTransactionException(
                    status, detail, revertReason.toHexString(), txnResult, params.isModularized());
        }
    }

    protected final void updateMetrics(CallServiceParameters parameters, long gasUsed, int iterations, String status) {
        var tags = Tags.of("iteration", String.valueOf(iterations))
                .and("modularized", String.valueOf(parameters.isModularized()))
                .and("type", parameters.getCallType().toString());
        invocationCounter.withTags(tags.and("status", status)).increment();
        gasUsedCounter.withTags(tags).increment(gasUsed);
    }

    protected final void updateGasLimitMetric(final CallServiceParameters parameters) {
        var tags = Tags.of("modularized", String.valueOf(parameters.isModularized()))
                .and("type", parameters.getCallType().toString());
        gasLimitCounter.withTags(tags).increment(parameters.getGas());
    }
}
