// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.exception.ResponseCodeUtil.getStatusOrDefault;

import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.CustomLog;
import org.springframework.transaction.annotation.Transactional;
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
            Store store,
            MirrorEvmTxProcessor mirrorEvmTxProcessor,
            Bucket gasLimitBucket,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
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
        this.contractActionRepository = contractActionRepository;
    }

    @Transactional(readOnly = true, timeoutString = "#{@web3Properties.getTransactionTimeout().toSeconds()}")
    public OpcodesProcessingResult processOpcodeCall(
            final @Valid ContractDebugParameters params, final OpcodeTracerOptions opcodeTracerOptions) {
        return ContractCallContext.run(ctx -> {
            ctx.setTimestamp(Optional.of(params.getConsensusTimestamp() - 1));
            ctx.setOpcodeTracerOptions(opcodeTracerOptions);
            ctx.setContractActions(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(
                    params.getConsensusTimestamp()));
            final var ethCallTxnResult = callContract(params, ctx);
            return new OpcodesProcessingResult(ethCallTxnResult, ctx.getOpcodes());
        });
    }

    @Override
    protected void validateResult(
            final HederaEvmTransactionProcessingResult txnResult, final CallServiceParameters params) {
        try {
            super.validateResult(txnResult, params);
        } catch (MirrorEvmTransactionException e) {
            log.warn(
                    "Transaction failed with status: {}, detail: {}, revertReason: {}",
                    getStatusOrDefault(txnResult),
                    e.getDetail(),
                    e.getData());
        }
    }
}
