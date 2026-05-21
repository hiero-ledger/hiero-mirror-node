// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import java.util.ArrayList;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class OpcodeServiceImpl extends TraceService implements OpcodeService {

    public OpcodeServiceImpl(
            final RecordFileService recordFileService,
            final ContractDebugService contractDebugService,
            final ContractBytecodeReadableKVState contractBytecodeReadableKVState,
            final ContractStorageReadableKVState contractStorageReadableKVState,
            final EthereumTransactionRepository ethereumTransactionRepository,
            final ContractResultRepository contractResultRepository,
            final CommonEntityAccessor commonEntityAccessor,
            final AccountReadableKVState accountReadableKVState,
            final ContractTransactionHashRepository contractTransactionHashRepository,
            final TransactionRepository transactionRepository) {
        super(
                recordFileService,
                contractDebugService,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                ethereumTransactionRepository,
                contractResultRepository,
                commonEntityAccessor,
                accountReadableKVState,
                contractTransactionHashRepository,
                transactionRepository);
    }

    @Override
    public OpcodesResponse processOpcodeCall(@NonNull OpcodeRequest opcodeRequest) {
        return ContractCallContext.run(ctx -> {
            final var params = buildCallServiceParameters(opcodeRequest.getTransactionIdOrHashParameter());
            final var opcodeContext = new OpcodeContext(opcodeRequest, (int) params.getGas() / 3);

            ctx.setOpcodeContext(opcodeContext);

            final OpcodesProcessingResult result = contractDebugService.processOpcodeCall(params, opcodeContext);
            return buildOpcodesResponse(result);
        });
    }

    private OpcodesResponse buildOpcodesResponse(@NonNull OpcodesProcessingResult result) {
        final var recipientAddress = result.recipient();
        Entity recipientEntity = null;
        if (recipientAddress != null && !recipientAddress.equals(EMPTY_ADDRESS)) {
            recipientEntity =
                    commonEntityAccessor.get(recipientAddress, Optional.empty()).orElse(null);
        }

        var address = EMPTY_ADDRESS.toHexString();
        String contractId = null;
        if (recipientEntity != null) {
            address = getEntityAddress(recipientEntity).toHexString();
            contractId = recipientEntity.toEntityId().toString();
        }

        final var txnResult = result.transactionProcessingResult();
        var returnValue = txnResult != null ? txnResult.contractCallResult() : HEX_PREFIX;
        if (returnValue == null || returnValue.isEmpty()) {
            returnValue = HEX_PREFIX;
        }

        final var opcodes = result.opcodes() != null ? result.opcodes() : new ArrayList<Opcode>();

        return new OpcodesResponse()
                .address(address)
                .contractId(contractId)
                .failed(txnResult == null || !txnResult.isSuccessful())
                .gas(txnResult != null ? txnResult.gasUsed() : 0L)
                .opcodes(opcodes)
                .returnValue(returnValue);
    }
}
