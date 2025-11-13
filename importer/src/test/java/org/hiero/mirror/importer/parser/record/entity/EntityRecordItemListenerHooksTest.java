// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.BytesValue;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageSlot;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EvmHookCall;
import com.hederahashgraph.api.proto.java.HookCall;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.hiero.mirror.importer.repository.ContractStateRepository;
import org.hiero.mirror.importer.repository.HookStorageChangeRepository;
import org.hiero.mirror.importer.repository.HookStorageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EntityRecordItemListenerHooksTest extends AbstractEntityRecordItemListenerTest {

    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final HookStorageChangeRepository hookStorageChangeRepository;
    private final HookStorageRepository hookStorageRepository;

    private ContractID hookSystemContractId;

    @BeforeEach
    void setup() {
        hookSystemContractId = recordItemBuilder.contractId(0x16d);
    }

    @Test
    void hookCall() {
        // given
        final var aliasAccount = domainBuilder.entity().persist();
        final var aliasAccountId = aliasAccount.toEntityId().toAccountID().toBuilder()
                .setAlias(DomainUtils.fromBytes(aliasAccount.getAlias()))
                .build();
        final var tokenTransferHookAccountId = recordItemBuilder.accountId();
        // - pre tx allowance hook for hbar transfer from alias account
        // - pre-post tx allowance hook for fungible token  transfer from non-alias account
        final var cryptoTransfer = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(recordItemBuilder.tokenId())
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(tokenTransferHookAccountId)
                                        .setAmount(-200L)
                                        .setPrePostTxAllowanceHook(HookCall.newBuilder()
                                                .setHookId(2)
                                                .setEvmHookCall(EvmHookCall.getDefaultInstance())))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(recordItemBuilder.accountId())
                                        .setAmount(200L)))
                        .setTransfers(TransferList.newBuilder()
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(aliasAccountId)
                                        .setAmount(-1000L)
                                        .setPreTxAllowanceHook(HookCall.newBuilder()
                                                .setEvmHookCall(EvmHookCall.getDefaultInstance())
                                                .setHookId(1)))
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(recordItemBuilder.accountId())
                                        .setAmount(1000L))))
                .build();

        // pre tx allowance hook for hbar transfer
        final var hookCall1StorageSlot = recordItemBuilder.slot();
        final var hookCall1StorageValue = recordItemBuilder.bytes(32);
        final var hookCall1 = recordItemBuilder
                .contractCall(hookSystemContractId)
                .customize(r -> customizeHookCall(r, 1, cryptoTransfer))
                .sidecarRecords(s -> {
                    final var storageChange = StorageChange.newBuilder()
                            .setSlot(hookCall1StorageSlot)
                            .setValueWritten(BytesValue.of(hookCall1StorageValue))
                            .build();
                    setHookContractStateChange(s, storageChange);
                })
                .build();

        // the pre tx execution of the pre-post tx allowance hook for fungible token transfer
        final var hookCall2StorageSlot = recordItemBuilder.slot();
        final var hookCall2StorageValue = recordItemBuilder.bytes(32);
        final var hookCall2 = recordItemBuilder
                .contractCall(hookSystemContractId)
                .customize(r -> customizeHookCall(r, 2, cryptoTransfer))
                .sidecarRecords(s -> {
                    final var storageChange = StorageChange.newBuilder()
                            .setSlot(hookCall2StorageSlot)
                            .setValueWritten(BytesValue.of(hookCall2StorageValue))
                            .build();
                    setHookContractStateChange(s, storageChange);
                })
                .build();

        // the post tx execution of the pre-post tx allowance hook for fungible token transfer
        final var hookCall3StorageValue = recordItemBuilder.bytes(32);
        final var hookCall3 = recordItemBuilder
                .contractCall(hookSystemContractId)
                .customize(r -> customizeHookCall(r, 3, cryptoTransfer))
                .sidecarRecords(s -> {
                    // update existing slot
                    final var storageChange = StorageChange.newBuilder()
                            .setSlot(hookCall2StorageSlot)
                            .setValueRead(hookCall2StorageValue)
                            .setValueWritten(BytesValue.of(hookCall3StorageValue))
                            .build();
                    setHookContractStateChange(s, storageChange);
                })
                .build();

        // when
        parseRecordItemsAndCommit(List.of(cryptoTransfer, hookCall1, hookCall2, hookCall3));

        // then
        assertThat(contractStateChangeRepository.findAll()).isEmpty();
        assertThat(contractStateRepository.findAll()).isEmpty();

        final var tokenTransferHookAccount =
                EntityId.of(tokenTransferHookAccountId).getId();
        final var expectedHookStorageChanges = List.of(
                HookStorageChange.builder()
                        .consensusTimestamp(hookCall1.getConsensusTimestamp())
                        .hookId(1)
                        .key(DomainUtils.toBytes(hookCall1StorageSlot))
                        .ownerId(aliasAccount.getId())
                        .valueRead(ArrayUtils.EMPTY_BYTE_ARRAY)
                        .valueWritten(DomainUtils.toBytes(hookCall1StorageValue))
                        .build(),
                HookStorageChange.builder()
                        .consensusTimestamp(hookCall2.getConsensusTimestamp())
                        .hookId(2)
                        .key(DomainUtils.toBytes(hookCall2StorageSlot))
                        .ownerId(tokenTransferHookAccount)
                        .valueRead(ArrayUtils.EMPTY_BYTE_ARRAY)
                        .valueWritten(DomainUtils.toBytes(hookCall2StorageValue))
                        .build(),
                HookStorageChange.builder()
                        .consensusTimestamp(hookCall3.getConsensusTimestamp())
                        .hookId(2)
                        .key(DomainUtils.toBytes(hookCall2StorageSlot))
                        .ownerId(tokenTransferHookAccount)
                        .valueRead(DomainUtils.toBytes(hookCall2StorageValue))
                        .valueWritten(DomainUtils.toBytes(hookCall3StorageValue))
                        .build());
        assertThat(hookStorageChangeRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedHookStorageChanges);

        final var expectedHookStorages = List.of(
                HookStorage.builder()
                        .createdTimestamp(hookCall1.getConsensusTimestamp())
                        .hookId(1)
                        .key(DomainUtils.leftPadBytes(DomainUtils.toBytes(hookCall1StorageSlot), 32))
                        .modifiedTimestamp(hookCall1.getConsensusTimestamp())
                        .ownerId(aliasAccount.getId())
                        .value(DomainUtils.toBytes(hookCall1StorageValue))
                        .build(),
                HookStorage.builder()
                        .createdTimestamp(hookCall2.getConsensusTimestamp())
                        .hookId(2)
                        .key(DomainUtils.leftPadBytes(DomainUtils.toBytes(hookCall2StorageSlot), 32))
                        .modifiedTimestamp(hookCall3.getConsensusTimestamp())
                        .ownerId(tokenTransferHookAccount)
                        .value(DomainUtils.toBytes(hookCall3StorageValue))
                        .build());
        assertThat(hookStorageRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedHookStorages);

        final long parentConsensusTimestamp = cryptoTransfer.getConsensusTimestamp();
        final var validStartTimestamp = DomainUtils.timestampInNanosMax(
                cryptoTransfer.getTransactionRecord().getTransactionID().getTransactionValidStart());
        final var expectedTransactions = List.of(
                Transaction.builder()
                        .consensusTimestamp(cryptoTransfer.getConsensusTimestamp())
                        .nonce(0)
                        .payerAccountId(cryptoTransfer.getPayerAccountId())
                        .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                        .validStartNs(validStartTimestamp)
                        .build(),
                Transaction.builder()
                        .consensusTimestamp(hookCall1.getConsensusTimestamp())
                        .entityId(EntityId.of(hookSystemContractId))
                        .nonce(1)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(cryptoTransfer.getPayerAccountId())
                        .type(TransactionType.CONTRACTCALL.getProtoId())
                        .validStartNs(validStartTimestamp)
                        .build(),
                Transaction.builder()
                        .consensusTimestamp(hookCall2.getConsensusTimestamp())
                        .entityId(EntityId.of(hookSystemContractId))
                        .nonce(2)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(cryptoTransfer.getPayerAccountId())
                        .type(TransactionType.CONTRACTCALL.getProtoId())
                        .validStartNs(validStartTimestamp)
                        .build(),
                Transaction.builder()
                        .consensusTimestamp(hookCall3.getConsensusTimestamp())
                        .entityId(EntityId.of(hookSystemContractId))
                        .nonce(3)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(cryptoTransfer.getPayerAccountId())
                        .type(TransactionType.CONTRACTCALL.getProtoId())
                        .validStartNs(validStartTimestamp)
                        .build());
        assertThat(transactionRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "consensusTimestamp",
                        "entityId",
                        "nonce",
                        "parentConsensusTimestamp",
                        "payerAccountId",
                        "type",
                        "validStartNs")
                .containsExactlyInAnyOrderElementsOf(expectedTransactions);
    }

    @Test
    void lambdaSStore() {
        // given
        final var slot = recordItemBuilder.slot();
        final var value = recordItemBuilder.bytes(32);
        final var slotUpdate = LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder().setKey(slot).setValue(value))
                .build();
        final var recordItem = recordItemBuilder
                .lambdaSStore()
                .transactionBody(l -> l.clearStorageUpdates().addStorageUpdates(slotUpdate))
                .build();
        final var lambdaSStore = recordItem.getTransactionBody().getLambdaSstore();
        final var expectedEntityId =
                EntityId.of(lambdaSStore.getHookId().getEntityId().getAccountId());

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), Transaction::getConsensusTimestamp)
                .returns(expectedEntityId, Transaction::getEntityId)
                .returns(recordItem.getPayerAccountId(), Transaction::getPayerAccountId)
                .returns(null, Transaction::getParentConsensusTimestamp)
                .returns(TransactionType.LAMBDA_SSTORE.getProtoId(), Transaction::getType);
        final var expectedHookStorageChange = HookStorageChange.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .hookId(lambdaSStore.getHookId().getHookId())
                .key(DomainUtils.toBytes(slot))
                .ownerId(expectedEntityId.getId())
                .valueRead(DomainUtils.toBytes(value))
                .valueWritten(DomainUtils.toBytes(value))
                .build();
        assertThat(hookStorageChangeRepository.findAll()).containsExactly(expectedHookStorageChange);
        final var expectedHookStorage = HookStorage.builder()
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .hookId(lambdaSStore.getHookId().getHookId())
                .key(DomainUtils.leftPadBytes(DomainUtils.toBytes(slot), 32))
                .ownerId(expectedEntityId.getId())
                .value(DomainUtils.toBytes(value))
                .modifiedTimestamp(recordItem.getConsensusTimestamp())
                .build();
        assertThat(hookStorageRepository.findAll()).containsExactly(expectedHookStorage);
    }

    private void customizeHookCall(
            final RecordItemBuilder.Builder<ContractCallTransactionBody.Builder> builder,
            final int nonce,
            final RecordItem parent) {
        final var transactionRecord = parent.getTransactionRecord();
        final var parentConsensusTimestamp = transactionRecord.getConsensusTimestamp();
        final var transactionId =
                transactionRecord.getTransactionID().toBuilder().setNonce(nonce).build();
        builder.record(r ->
                        r.setParentConsensusTimestamp(parentConsensusTimestamp).setTransactionID(transactionId))
                .recordItem(r -> r.parent(parent))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId));
    }

    private void setHookContractStateChange(
            final List<TransactionSidecarRecord.Builder> sidecarRecordBuilders, StorageChange storageChange) {
        sidecarRecordBuilders.clear();
        sidecarRecordBuilders.add(TransactionSidecarRecord.newBuilder()
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(ContractStateChange.newBuilder()
                                .setContractId(hookSystemContractId)
                                .addStorageChanges(storageChange))));
    }
}
