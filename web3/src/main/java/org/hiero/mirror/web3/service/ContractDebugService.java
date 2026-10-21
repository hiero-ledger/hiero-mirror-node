// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.TraceTimeoutException;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Named
@Validated
public class ContractDebugService extends ContractCallService {
    private final ContractActionRepository contractActionRepository;

    @SuppressWarnings("java:S107")
    public ContractDebugService(
            ContractActionRepository contractActionRepository,
            RecordFileService recordFileService,
            ThrottleManager throttleManager,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
            EvmProperties evmProperties,
            TransactionExecutionService transactionExecutionService) {
        super(
                throttleManager,
                throttleProperties,
                meterRegistry,
                recordFileService,
                evmProperties,
                transactionExecutionService);
        this.contractActionRepository = contractActionRepository;
    }

    public OpcodesProcessingResult processOpcodeCall(
            final @Valid ContractDebugParameters params, final OpcodeContext opcodeContext) {
        final var ctx = ContractCallContext.get();
        ctx.setTimestamp(Optional.of(params.getConsensusTimestamp() - 1));
        ctx.setOpcodeContext(opcodeContext);
        ctx.getOpcodeContext()
                .setActions(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(
                        params.getConsensusTimestamp(), params.getPayerAccountId()));
        final var ethCallTxnResult = callContract(params, ctx);
        return new OpcodesProcessingResult(
                ethCallTxnResult, params.getReceiver(), ctx.getOpcodeContext().getOpcodes());
    }

    public List<ActionResponse> processTraceCall(final @Valid TraceCall traceCall) {
        return ContractCallContext.run(ctx -> {
            ctx.setApi(ACTIONS);
            ctx.setDeadlineMillis(ctx.getStartTime() + traceCall.getTimeout().toMillis());
            final var actionContext = ActionContext.builder().build();
            ctx.setActionContext(actionContext);
            actionContext.setOnlyTopCall(traceCall.isOnlyTopCall());
            final var stateOverride = traceCall.getStateOverride();
            ctx.applyStateOverrides(stateOverride == null ? List.of() : List.of(stateOverride));

            final var bundles = traceCall.getBundles();
            for (int bundleIndex = 0; bundleIndex < bundles.size(); bundleIndex++) {
                final var bundle = bundles.get(bundleIndex);
                // The first bundle starts clean. Later bundles keep account and storage changes.
                if (bundleIndex == 0) {
                    ctx.clearCaches();
                }
                applyBundleBlockOverride(ctx, bundle.getBlockOverride());
                if (bundleIndex > 0) {
                    // Drop cached block headers so this bundle's number and time are read again.
                    ctx.evictCachedState(BLOCKS_STATE_ID);
                    ctx.evictCachedState(BLOCK_STREAM_INFO_STATE_ID);
                }

                final var transactions = bundle.getTransactions();
                for (final var transaction : transactions) {
                    actionContext.beginCall();
                    try {
                        callContract(transaction, ctx);
                    } catch (final QueryTimeoutException _) {
                        throw new TraceTimeoutException(actionResponse(ctx));
                    } catch (final MirrorEvmTransactionException e) {
                        if (actionContext.isTimedOut()) {
                            throw new TraceTimeoutException(actionResponse(ctx));
                        }
                        throw e;
                    }
                    if (actionContext.isTimedOut()) {
                        throw new TraceTimeoutException(actionResponse(ctx));
                    }
                }
            }
            return actionResponse(ctx);
        });
    }

    /**
     * A bundle with {@code block_override} applies the fields it sets. An omitted block number keeps the previous
     * number and increases it by 1. An omitted timestamp keeps the previous timestamp and increases it by 2 seconds. A
     * later bundle without an override keeps that context and advances both.
     */
    private static void applyBundleBlockOverride(
            final ContractCallContext ctx, final @Nullable BlockOverride override) {
        if (override == null) {
            if (ctx.hasBlockOverride()) {
                ctx.advanceBlockOverride();
            }
            return;
        }
        final var omittedNumber = StringUtils.isBlank(override.getNumber());
        final var omittedTime = StringUtils.isBlank(override.getTime());
        final var previousNumber = ctx.getBlockOverrideNumber();
        final var previousTime = ctx.getBlockOverrideTimeNanos();
        ctx.applyBlockOverride(override);
        try {
            if (omittedNumber && previousNumber != null) {
                ctx.setBlockOverrideNumber(Math.addExact(previousNumber, 1L));
            }
            if (omittedTime && previousTime != null) {
                ctx.setBlockOverrideTimeNanos(Math.addExact(previousTime, DomainUtils.convertToNanosMax(2L, 0L)));
            }
        } catch (ArithmeticException e) {
            throw new InvalidParametersException("Invalid block_override: block context overflow");
        }
    }

    private List<ActionResponse> actionResponse(final ContractCallContext ctx) {
        return List.copyOf(ctx.getActionContext().getActions());
    }

    @Override
    protected void validateResult(final EvmTransactionResult txnResult, final CallServiceParameters params) {
        try {
            super.validateResult(txnResult, params);
        } catch (MirrorEvmTransactionException e) {
            log.warn(
                    "Transaction failed with status: {}, detail: {}, revertReason: {}",
                    txnResult.responseCodeEnum(),
                    e.getDetail(),
                    e.getData());
        }
    }
}
