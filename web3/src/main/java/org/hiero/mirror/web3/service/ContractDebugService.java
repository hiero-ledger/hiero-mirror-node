// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.TRACE;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.rest.model.TracerResponse;
import org.hiero.mirror.rest.model.TracerResponseActions;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.controller.TracerProperties;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
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
import org.hiero.mirror.web3.viewmodel.TracerConfig;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Named
@Validated
public class ContractDebugService extends ContractCallService {
    private final ContractActionRepository contractActionRepository;
    private final TracerProperties tracerProperties;
    private final Web3Properties web3Properties;

    @SuppressWarnings("java:S107")
    public ContractDebugService(
            ContractActionRepository contractActionRepository,
            TracerProperties tracerProperties,
            Web3Properties web3Properties,
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
        this.tracerProperties = tracerProperties;
        this.web3Properties = web3Properties;
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

    public TracerResponse processTraceCall(final @Valid TraceRequest traceRequest) {
        return ContractCallContext.run(ctx -> {
            final var effectiveTimeout = resolveTimeout(traceRequest.getTimeout());
            ctx.setApi(TRACE);
            ctx.setDeadlineMillis(ctx.getStartTime() + effectiveTimeout.toMillis());
            ctx.applyStateOverrides(
                    traceRequest.getContractExecutionParameters().getStateOverrides());

            final var actionContext = ActionContext.builder()
                    .tracerConfig(TracerConfig.builder()
                            .onlyTopCall(traceRequest.isOnlyTopCall())
                            .timeout(effectiveTimeout.toString())
                            .tracerType(TracerType.ACTION)
                            .build())
                    .build();
            ctx.setActionContext(actionContext);

            try {
                callContract(traceRequest.getContractExecutionParameters(), ctx);
            } catch (QueryTimeoutException e) {
                throw new TraceTimeoutException(tracerResponse(ctx));
            } catch (MirrorEvmTransactionException e) {
                if (actionContext.isTimedOut()) {
                    throw new TraceTimeoutException(tracerResponse(ctx));
                }
                throw e;
            }

            if (actionContext.isTimedOut()) {
                throw new TraceTimeoutException(tracerResponse(ctx));
            }
            return tracerResponse(ctx);
        });
    }

    private TracerResponse tracerResponse(final ContractCallContext ctx) {
        return new TracerResponse()
                .actions(
                        new TracerResponseActions().calls(ctx.getActionContext().getActions()));
    }

    private Duration resolveTimeout(final Duration requested) {
        final var maxTimeout = tracerProperties.getMaxTimeout();
        if (requested == null || requested.isNegative() || requested.isZero()) {
            return web3Properties.getRequestTimeout();
        }
        return requested.compareTo(maxTimeout) > 0 ? maxTimeout : requested;
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
