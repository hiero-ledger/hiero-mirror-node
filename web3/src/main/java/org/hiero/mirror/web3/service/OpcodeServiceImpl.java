// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCREATEINSTANCE;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodesResponseDto;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class OpcodeServiceImpl implements OpcodeService {

    private static final Address EMPTY_ADDRESS = Address.ZERO;
    private static final BigInteger ZERO = BigInteger.ZERO;

    private final RecordFileService recordFileService;
    private final ContractDebugService contractDebugService;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final ContractResultRepository contractResultRepository;
    private final CommonEntityAccessor commonEntityAccessor;

    @Override
    public OpcodesResponseDto processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHashParameter, @NonNull OpcodeProperties options) {
        final ContractDebugParameters params = buildCallServiceParameters(transactionIdOrHashParameter);
        return ContractCallContext.run(ctx -> {
            final OpcodesProcessingResult result = contractDebugService.processOpcodeCall(params, options);
            return buildOpcodesResponse(result);
        });
    }

    private ContractDebugParameters buildCallServiceParameters(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash) {
        final Long consensusTimestamp;
        final Transaction transaction;
        final EthereumTransaction ethereumTransaction;

        switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash -> {
                ContractTransactionHash contractTransactionHash = contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArray())
                        .orElseThrow(() ->
                                new EntityNotFoundException("Contract transaction hash not found: " + transactionHash));

                transaction = null;
                consensusTimestamp = contractTransactionHash.getConsensusTimestamp();
                ethereumTransaction = ethereumTransactionRepository
                        .findByConsensusTimestampAndPayerAccountId(
                                consensusTimestamp, EntityId.of(contractTransactionHash.getPayerAccountId()))
                        .orElse(null);
            }
            case TransactionIdParameter transactionId -> {
                final var validStartNs = convertToNanosMax(transactionId.validStart());
                final var payerAccountId = transactionId.payerAccountId();

                final var transactionList =
                        transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                                payerAccountId, validStartNs);
                if (transactionList.isEmpty()) {
                    throw new EntityNotFoundException("Transaction not found: " + transactionId);
                }

                final var parentTransaction = transactionList.getFirst();
                transaction = parentTransaction;
                consensusTimestamp = parentTransaction.getConsensusTimestamp();
                ethereumTransaction = ethereumTransactionRepository
                        .findByConsensusTimestampAndPayerAccountId(
                                consensusTimestamp, parentTransaction.getPayerAccountId())
                        .orElse(null);
            }
        }

        return buildCallServiceParameters(consensusTimestamp, transaction, ethereumTransaction);
    }

    private OpcodesResponseDto buildOpcodesResponse(@NonNull OpcodesProcessingResult result) {
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

        return new OpcodesResponseDto(
                address,
                contractId,
                txnResult == null || !txnResult.isSuccessful(),
                txnResult != null ? txnResult.gasUsed() : 0L,
                opcodes,
                returnValue);
    }

    private ContractDebugParameters buildCallServiceParameters(
            Long consensusTimestamp, Transaction transaction, EthereumTransaction ethTransaction) {
        final var contractResult = contractResultRepository
                .findById(consensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Contract result not found: " + consensusTimestamp));

        final var blockType = recordFileService
                .findByTimestamp(consensusTimestamp)
                .map(recordFile -> BlockType.of(recordFile.getIndex().toString()))
                .orElse(BlockType.LATEST);

        final var transactionType = transaction != null ? transaction.getType() : TransactionType.UNKNOWN.getProtoId();

        return ContractDebugParameters.builder()
                .block(blockType)
                .callDataBytes(getCallDataBytes(ethTransaction, contractResult))
                .ethereumDataBytes(getEthereumDataBytes(ethTransaction))
                .consensusTimestamp(consensusTimestamp)
                .gas(getGasLimit(ethTransaction, contractResult))
                .receiver(getReceiverAddress(ethTransaction, contractResult, transactionType))
                .sender(getSenderAddress(contractResult))
                .value(getValue(ethTransaction, contractResult).longValue())
                .build();
    }

    private Address getSenderAddress(ContractResult contractResult) {
        final var address = commonEntityAccessor.evmAddressFromId(contractResult.getSenderId(), Optional.empty());
        return address != null ? address : EMPTY_ADDRESS;
    }

    private Address getReceiverAddress(
            EthereumTransaction ethereumTransaction, ContractResult contractResult, int transactionType) {
        if (ethereumTransaction != null) {
            if (ArrayUtils.isEmpty(ethereumTransaction.getToAddress())) {
                return EMPTY_ADDRESS;
            }
            final var address = Address.wrap(Bytes.wrap(ethereumTransaction.getToAddress()));
            if (ConversionUtils.isLongZero(address)) {
                final var entity =
                        commonEntityAccessor.get(address, Optional.empty()).orElse(null);
                if (entity != null) {
                    return getEntityAddress(entity);
                }
            }
            return address;
        }

        if (transactionType == CONTRACTCREATEINSTANCE.getProtoId()) {
            return EMPTY_ADDRESS;
        }
        final var contractId = EntityId.of(contractResult.getContractId());
        final var address = commonEntityAccessor.evmAddressFromId(contractId, Optional.empty());
        return address != null ? address : EMPTY_ADDRESS;
    }

    private Long getGasLimit(EthereumTransaction ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction != null ? ethereumTransaction.getGasLimit() : contractResult.getGasLimit();
    }

    private BigInteger getValue(EthereumTransaction ethereumTransaction, ContractResult contractResult) {
        if (ethereumTransaction != null) {
            return new BigInteger(ethereumTransaction.getValue());
        }
        if (contractResult.getAmount() != null) {
            return BigInteger.valueOf(contractResult.getAmount());
        }
        return ZERO;
    }

    private byte[] getCallDataBytes(EthereumTransaction ethereumTransaction, ContractResult contractResult) {
        final var callData = ethereumTransaction != null
                ? ethereumTransaction.getCallData()
                : contractResult.getFunctionParameters();
        return callData != null ? callData : new byte[0];
    }

    private byte[] getEthereumDataBytes(EthereumTransaction ethereumTransaction) {
        if (ethereumTransaction == null) {
            return new byte[0];
        }
        final var data = ethereumTransaction.getData();
        return data != null ? data : new byte[0];
    }

    private Address getEntityAddress(Entity entity) {
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return EntityId.isEmpty(entity.toEntityId()) ? EMPTY_ADDRESS : toAddress(entity.toEntityId());
    }
}
