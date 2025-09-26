// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCREATEINSTANCE;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class OpcodeServiceImpl implements OpcodeService {

    private final RecordFileService recordFileService;
    private final ContractDebugService contractDebugService;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public OpcodesResponse processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHashParameter, @NonNull OpcodeTracerOptions options) {
        final ContractDebugParameters params =
                buildCallServiceParameters(transactionIdOrHashParameter, options.isModularized());
        final OpcodesProcessingResult result = contractDebugService.processOpcodeCall(params, options);
        return buildOpcodesResponse(result);
    }

    private ContractDebugParameters buildCallServiceParameters(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash, boolean isModularized) {
        final Long consensusTimestamp;
        final Optional<Transaction> transaction;
        final Optional<EthereumTransaction> ethereumTransaction;

        switch (transactionIdOrHash) {
            case TransactionHashParameter transactionHash -> {
                ContractTransactionHash contractTransactionHash = contractTransactionHashRepository
                        .findByHash(transactionHash.hash().toArray())
                        .orElseThrow(() ->
                                new EntityNotFoundException("Contract transaction hash not found: " + transactionHash));

                transaction = Optional.empty();
                consensusTimestamp = contractTransactionHash.getConsensusTimestamp();
                ethereumTransaction = ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        consensusTimestamp, EntityId.of(contractTransactionHash.getPayerAccountId()));
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
                transaction = Optional.of(parentTransaction);
                consensusTimestamp = parentTransaction.getConsensusTimestamp();
                ethereumTransaction = ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        consensusTimestamp, parentTransaction.getPayerAccountId());
            }
        }

        return buildCallServiceParameters(consensusTimestamp, transaction, ethereumTransaction, isModularized);
    }

    private OpcodesResponse buildOpcodesResponse(@NonNull OpcodesProcessingResult result) {
        final Optional<Address> recipientAddress =
                result.transactionProcessingResult().getRecipient();

        final Optional<Entity> recipientEntity =
                recipientAddress.flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()));

        return new OpcodesResponse()
                .address(recipientEntity
                        .map(this::getEntityAddress)
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .contractId(recipientEntity
                        .map(Entity::toEntityId)
                        .map(EntityId::toString)
                        .orElse(null))
                .failed(!result.transactionProcessingResult().isSuccessful())
                .gas(result.transactionProcessingResult().getGasUsed())
                .opcodes(result.opcodes().stream()
                        .map(opcode -> new Opcode()
                                .depth(opcode.depth())
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .op(opcode.op())
                                .pc(opcode.pc())
                                .reason(opcode.reason())
                                .stack(opcode.stack().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .memory(opcode.memory().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .storage(opcode.storage().entrySet().stream()
                                        .collect(Collectors.toMap(
                                                entry -> entry.getKey().toHexString(),
                                                entry -> entry.getValue().toHexString()))))
                        .toList())
                .returnValue(
                        Optional.ofNullable(result.transactionProcessingResult().getOutput())
                                .map(Bytes::toHexString)
                                .orElse(Bytes.EMPTY.toHexString()));
    }

    private ContractDebugParameters buildCallServiceParameters(
            Long consensusTimestamp,
            Optional<Transaction> transaction,
            Optional<EthereumTransaction> ethTransaction,
            boolean isModularized) {
        final ContractResult contractResult = contractResultRepository
                .findById(consensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Contract result not found: " + consensusTimestamp));

        final BlockType blockType = recordFileService
                .findByTimestamp(consensusTimestamp)
                .map(recordFile -> BlockType.of(recordFile.getIndex().toString()))
                .orElse(BlockType.LATEST);

        final Integer transactionType =
                transaction.map(Transaction::getType).orElse(TransactionType.UNKNOWN.getProtoId());

        return ContractDebugParameters.builder()
                .block(blockType)
                .callData(getCallData(ethTransaction, contractResult))
                .consensusTimestamp(consensusTimestamp)
                .gas(getGasLimit(ethTransaction, contractResult))
                .isModularized(isModularized)
                .receiver(getReceiverAddress(ethTransaction, contractResult, transactionType))
                .sender(getSenderAddress(contractResult))
                .value(getValue(ethTransaction, contractResult).longValue())
                .build();
    }

    private Address getSenderAddress(ContractResult contractResult) {
        return entityDatabaseAccessor.evmAddressFromId(contractResult.getSenderId(), Optional.empty());
    }

    private Address getReceiverAddress(
            Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult, Integer transactionType) {
        return ethereumTransaction
                .flatMap(transaction -> {
                    if (ArrayUtils.isEmpty(transaction.getToAddress())) {
                        return Optional.of(Address.ZERO);
                    }
                    Address address = Address.wrap(Bytes.wrap(transaction.getToAddress()));
                    if (isMirror(address.toArrayUnsafe())) {
                        return entityDatabaseAccessor
                                .get(address, Optional.empty())
                                .map(this::getEntityAddress);
                    }
                    return Optional.of(address);
                })
                .orElseGet(() -> {
                    if (transactionType.equals(CONTRACTCREATEINSTANCE.getProtoId())) {
                        return Address.ZERO;
                    }
                    final var contractId = EntityId.of(contractResult.getContractId());
                    return entityDatabaseAccessor.evmAddressFromId(contractId, Optional.empty());
                });
    }

    private Long getGasLimit(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction.map(EthereumTransaction::getGasLimit).orElse(contractResult.getGasLimit());
    }

    private BigInteger getValue(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        return ethereumTransaction
                .map(transaction -> new BigInteger(transaction.getValue()))
                .or(() -> Optional.ofNullable(contractResult.getAmount()).map(BigInteger::valueOf))
                .orElse(BigInteger.ZERO);
    }

    private Bytes getCallData(Optional<EthereumTransaction> ethereumTransaction, ContractResult contractResult) {
        final byte[] callData = ethereumTransaction
                .map(EthereumTransaction::getCallData)
                .orElse(contractResult.getFunctionParameters());

        return Optional.ofNullable(callData).map(Bytes::of).orElse(Bytes.EMPTY);
    }

    private Address getEntityAddress(Entity entity) {
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return EntityId.isEmpty(entity.toEntityId()) ? Address.ZERO : toAddress(entity.toEntityId());
    }
}
