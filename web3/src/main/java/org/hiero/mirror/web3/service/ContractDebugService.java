// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.TRACE;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
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
                        params.getConsensusTimestamp()));
        final var ethCallTxnResult = callContract(params, ctx);
        return new OpcodesProcessingResult(
                ethCallTxnResult, params.getReceiver(), ctx.getOpcodeContext().getOpcodes());
    }

    public ActionResponse processTraceCall(final @Valid TraceRequest traceRequest) {
        return ContractCallContext.run(ctx -> {
            ctx.setApi(TRACE);
            final var timeout = traceRequest.getTimeout();
            if (timeout != null) {
                ctx.setDeadlineMillis(ctx.getStartTime() + timeout.toMillis());
            }
            ctx.applyStateOverrides(
                    traceRequest.getContractExecutionParameters().getStateOverrides());

            final var actionContext = ActionContext.builder()
                    .onlyTopCall(traceRequest.isOnlyTopCall())
                    .build();
            ctx.setActionContext(actionContext);

            try {
                callContract(traceRequest.getContractExecutionParameters(), ctx);
            } catch (final QueryTimeoutException e) {
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
            return actionResponse(ctx);
        });
    }

    private ActionResponse actionResponse(final ContractCallContext ctx) {
        return new ActionResponse().calls(ctx.getActionContext().getActions());
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
