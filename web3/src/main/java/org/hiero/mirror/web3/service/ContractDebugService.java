// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.rest.model.TracerResponse;
import org.hiero.mirror.rest.model.TracerResponseActions;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.TracerConfig;
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

    public TracerResponse processTraceCall(final @Valid TraceRequest traceRequest) {
        return ContractCallContext.run(ctx -> {
            final var actionContext = ActionContext.builder()
                    .tracerConfig(TracerConfig.builder()
                            .onlyTopCall(traceRequest.isOnlyTopCall())
                            .tracerType(TracerType.ACTION)
                            .build())
                    .build();
            ctx.setActionContext(actionContext);

            callContract(traceRequest.getContractExecutionParameters(), ctx);

            return new TracerResponse()
                    .actions(new TracerResponseActions()
                            .calls(ctx.getActionContext().getActions()));
        });
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
