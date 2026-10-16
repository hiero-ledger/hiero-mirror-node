// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
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
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Named
@Validated
public class ContractDebugService extends ContractCallService {
    private final ContractActionRepository contractActionRepository;
    private final ThrottleManager throttleManager;

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
        this.throttleManager = throttleManager;
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

    public List<ActionResponse> processTraceCall(final @Valid List<TraceRequest> traceRequests) {
        if (traceRequests.isEmpty()) {
            throw new InvalidParametersException("At least one action trace request is required");
        }
        return ContractCallContext.run(ctx -> {
            ctx.setApi(ACTIONS);
            final var first = traceRequests.getFirst();
            ctx.setDeadlineMillis(ctx.getStartTime() + first.getTimeout().toMillis());
            final var actionContext = ActionContext.builder().build();
            ctx.setActionContext(actionContext);

            for (int i = 0; i < traceRequests.size(); i++) {
                final var traceRequest = traceRequests.get(i);
                actionContext.setOnlyTopCall(traceRequest.isOnlyTopCall());
                actionContext.beginCall();
                final var params = traceRequest.getContractExecutionParameters();
                try {
                    ctx.applyStateOverrides(params.getStateOverrides());
                    ctx.applyBlockOverride(traceRequest.getBlockOverride());
                    callContract(params, ctx);
                } catch (final QueryTimeoutException e) {
                    throw new TraceTimeoutException(actionResponse(ctx));
                } catch (final MirrorEvmTransactionException e) {
                    if (actionContext.isTimedOut()) {
                        throw new TraceTimeoutException(actionResponse(ctx));
                    }
                    throw e;
                } catch (final IllegalArgumentException | InvalidParametersException e) {
                    throttleManager.restore(remainingGas(traceRequests, i));
                    throw e;
                }
                if (actionContext.isTimedOut()) {
                    throw new TraceTimeoutException(actionResponse(ctx));
                }
            }
            return actionResponse(ctx);
        });
    }

    private static long remainingGas(final List<TraceRequest> traceRequests, final int fromIndex) {
        var gas = 0L;
        for (int i = fromIndex; i < traceRequests.size(); i++) {
            gas += traceRequests.get(i).getContractExecutionParameters().getGas();
        }
        return gas;
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
