// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCREATEINSTANCE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static org.hiero.mirror.common.util.CommonUtils.nextBytes;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.DomainWrapper;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hyperledger.besu.datatypes.Address;

@Getter
@RequiredArgsConstructor
public enum TransactionProviderEnum {
    CONTRACT_CREATE(Instant.ofEpochSecond(1, 2000), CONTRACTCREATEINSTANCE, EthTransactionType.LEGACY.getTypeByte()),
    CONTRACT_CALL(Instant.ofEpochSecond(2, 3000), CONTRACTCALL, EthTransactionType.LEGACY.getTypeByte()),
    EIP1559(Instant.ofEpochSecond(3, 4000), ETHEREUMTRANSACTION, EthTransactionType.EIP_1559.getTypeByte()),
    EIP2930(Instant.ofEpochSecond(4, 5000), ETHEREUMTRANSACTION, EthTransactionType.EIP_2930.getTypeByte()),
    LEGACY(Instant.ofEpochSecond(5, 6000), ETHEREUMTRANSACTION, EthTransactionType.LEGACY.getTypeByte());

    private final Instant consensusTimestamp;
    private final TransactionType transactionType;
    private final int typeByte;
    private final long amount = 1000L;
    private final byte[] hash = nextBytes(32);
    private final byte[] callData = nextBytes(256);

    private DomainBuilder domainBuilder = new DomainBuilder();

    private Consumer<TransactionProviderEnum> customizer = ignored -> {};

    @Setter
    private EntityId contractId;

    @Setter
    private byte[] contractEvmAddress;

    @Setter
    private byte[] contractAlias;

    @Setter
    private EntityId payerAccountId;

    @Setter
    private byte[] payerEvmAddress;

    @Setter
    private byte[] payerAlias;

    public TransactionProviderEnum customize(Consumer<TransactionProviderEnum> consumer) {
        this.customizer = consumer;
        return this;
    }

    public void init(DomainBuilder domainBuilder) {
        this.domainBuilder = domainBuilder;
        this.contractId = domainBuilder.entityId();
        this.contractEvmAddress = domainBuilder.evmAddress();
        this.contractAlias = domainBuilder.key();
        this.payerAccountId = domainBuilder.entityId();
        this.payerEvmAddress = domainBuilder.evmAddress();
        this.payerAlias = domainBuilder.key();
        this.customizer.accept(this);
        // reset this to an empty consumer after applying the customizations
        this.customizer = ignored -> {};
    }

    public long getContractIdRaw() {
        return contractId == null || transactionType == CONTRACTCREATEINSTANCE ? 0L : contractId.getId();
    }

    public long getPayerAccountIdRaw() {
        return payerAccountId == null ? 0L : payerAccountId.getId();
    }

    public DomainWrapper<Transaction, Transaction.TransactionBuilder> getTransaction() {
        return domainBuilder.transaction().customize(tx -> {
            tx.type(transactionType.getProtoId());
            tx.memo("%s_%d".formatted(transactionType.name(), typeByte).getBytes());
            tx.transactionHash(hash);
            tx.payerAccountId(payerAccountId);
            tx.entityId(contractId);
            tx.validStartNs(
                    convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano() - 1000));
            tx.consensusTimestamp(convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano()));
        });
    }

    public boolean hasEthTransaction() {
        return transactionType == ETHEREUMTRANSACTION;
    }

    public DomainWrapper<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> getEthTransaction() {
        if (!hasEthTransaction()) {
            return domainBuilder.wrap(EthereumTransaction.builder(), () -> null);
        }

        final byte[] evmAddress;
        if (contractEvmAddress != null && contractEvmAddress.length == EVM_ADDRESS_LENGTH) {
            evmAddress = contractEvmAddress;
        } else if (contractAlias != null && contractAlias.length == EVM_ADDRESS_LENGTH) {
            evmAddress = contractAlias;
        } else if (!EntityId.isEmpty(contractId)) {
            evmAddress = toAddress(contractId).toArray();
        } else {
            evmAddress = new byte[0];
        }

        return domainBuilder.ethereumTransaction(true).customize(tx -> {
            tx.type(typeByte);
            tx.hash(hash);
            tx.value(ByteBuffer.allocate(Long.BYTES).putLong(amount).array());
            tx.payerAccountId(payerAccountId);
            tx.toAddress(evmAddress);
            tx.callData(callData);
            tx.consensusTimestamp(convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano()));
            if (typeByte == EthTransactionType.EIP_1559.getTypeByte()) {
                tx.maxGasAllowance(Long.MAX_VALUE);
                tx.maxFeePerGas(nextBytes(32));
                tx.maxPriorityFeePerGas(nextBytes(32));
            }
            if (typeByte == EthTransactionType.EIP_2930.getTypeByte()) {
                tx.accessList(nextBytes(100));
            }
        });
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> getRecordFile() {
        return domainBuilder.recordFile().customize(recordFile -> {
            recordFile.consensusStart(
                    convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano() - 1000));
            recordFile.consensusEnd(
                    convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano() + 1000));
        });
    }

    public DomainWrapper<ContractTransactionHash, ContractTransactionHash.ContractTransactionHashBuilder>
            getContractTransactionHash() {
        return domainBuilder.contractTransactionHash().customize(contractTransactionHash -> {
            contractTransactionHash.consensusTimestamp(
                    convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano()));
            contractTransactionHash.entityId(getContractIdRaw());
            contractTransactionHash.hash(hash);
            contractTransactionHash.payerAccountId(getPayerAccountIdRaw());
            contractTransactionHash.transactionResult(ResponseCodeEnum.SUCCESS_VALUE);
        });
    }

    public DomainWrapper<ContractResult, ContractResult.ContractResultBuilder<?, ?>> getContractResult() {
        return domainBuilder.contractResult().customize(result -> {
            result.amount(amount);
            result.consensusTimestamp(
                    convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano()));
            result.contractId(transactionType == CONTRACTCREATEINSTANCE ? 0L : getContractIdRaw());
            result.createdContractIds(
                    transactionType == CONTRACTCREATEINSTANCE ? List.of(getContractIdRaw()) : Collections.emptyList());
            result.functionParameters(callData);
            result.payerAccountId(payerAccountId);
            result.senderId(payerAccountId);
            result.transactionHash(hash);
        });
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> getContractEntity() {
        if (transactionType == CONTRACTCREATEINSTANCE) {
            return domainBuilder.wrap(Entity.builder(), () -> null);
        }
        final long createdAt = convertToNanosMax(consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano());
        return domainBuilder.entity(getContractIdRaw(), createdAt).customize(entity -> {
            entity.alias(contractAlias);
            entity.evmAddress(contractEvmAddress);
        });
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> getSenderEntity() {
        return domainBuilder
                .entity(getPayerAccountIdRaw(), domainBuilder.timestamp())
                .customize(entity -> {
                    entity.alias(payerAlias);
                    entity.evmAddress(payerEvmAddress);
                });
    }

    public static Address entityAddress(Entity entity) {
        if (entity == null) {
            return Address.ZERO;
        }
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return EntityId.isEmpty(entity.toEntityId()) ? Address.ZERO : toAddress(entity.toEntityId());
    }
}
