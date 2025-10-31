// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hedera.services.stream.proto.ContractAction.CallerCase.CALLING_CONTRACT;
import static com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody.StakedIdCase.STAKEDID_NOT_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.hiero.mirror.importer.TestUtils.toEntityTransaction;
import static org.hiero.mirror.importer.TestUtils.toEntityTransactions;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.hiero.mirror.importer.repository.ContractStateRepository;
import org.hiero.mirror.importer.repository.HookRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerContractTest extends AbstractEntityRecordItemListenerTest {

    private static final Version HAPI_VERSION_0_23_0 = new Version(0, 23, 0);
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final HookRepository hookRepository;

    // saves the mapping from proto ContractID to EntityId so as not to use EntityIdService to verify itself
    private Map<ContractID, EntityId> contractIds;

    @BeforeEach
    void before() {
        contractIds = new HashMap<>();
        entityProperties.getPersist().setEntityTransactions(true);
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setContracts(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setEntityTransactions(false);
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void contractCreate(boolean bytecodeSourceFileId, boolean hasAutoRenewAccount) {
        var stakedAccountId = domainBuilder.entityId().toAccountID();
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .transactionBody(b -> {
                    if (!bytecodeSourceFileId) {
                        b.clearFileID().setInitcode(recordItemBuilder.bytes(1));
                    }
                    if (!hasAutoRenewAccount) {
                        b.clearAutoRenewAccountId();
                    }
                    b.clearAutoRenewAccountId()
                            .setDeclineReward(true)
                            .setMaxAutomaticTokenAssociations(-1)
                            .setStakedAccountId(stakedAccountId);
                })
                .sidecarRecords(sidecars -> {
                    if (!bytecodeSourceFileId) {
                        sidecars.get(2).getBytecodeBuilder().clearInitcode();
                    }
                })
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var entityId = EntityId.of(txnRecord.getContractCreateResult().getContractID());
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(entityRepository.findById(entityId.getId()))
                        .get()
                        .returns(1L, Entity::getEthereumNonce)
                        .returns(-1, Entity::getMaxAutomaticTokenAssociations),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, txnRecord),
                () -> assertContractStateChanges(recordItem),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCreateChildNonce() {
        ContractID parentContractID =
                ContractID.newBuilder().setContractNum(1000L).build();
        ContractID childContractID =
                ContractID.newBuilder().setContractNum(1001L).build();
        var parentRecordItem = recordItemBuilder
                .contractCreate(parentContractID)
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .record(r -> r.setContractCreateResult(r.getContractCreateResult().toBuilder()
                        .clearContractNonces()
                        .addContractNonces(ContractNonceInfo.newBuilder()
                                .setContractId(parentContractID)
                                .setNonce(2))
                        .addContractNonces(ContractNonceInfo.newBuilder()
                                .setContractId(childContractID)
                                .setNonce(1))))
                .build();
        var txnRecord = parentRecordItem.getTransactionRecord();
        var transactionBody = parentRecordItem.getTransactionBody().getContractCreateInstance();
        EntityId parentId = EntityId.of(parentContractID);
        EntityId createdId = EntityId.of(childContractID);

        var childRecordItem = recordItemBuilder
                .contractCreate(childContractID)
                .record(r -> r.setTransactionID(
                                txnRecord.getTransactionID().toBuilder().setNonce(1))
                        .setParentConsensusTimestamp(txnRecord.getConsensusTimestamp())
                        .setContractCreateResult(
                                r.getContractCreateResult().toBuilder().clearContractNonces()))
                .build();
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(3, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(8, cryptoTransferRepository.count()),
                () -> assertContractEntity(parentRecordItem),
                () -> assertContractCreateResult(transactionBody, txnRecord),
                () -> assertThat(entityRepository.findById(createdId.getId()))
                        .get()
                        .returns(1L, Entity::getEthereumNonce),
                () -> assertThat(entityRepository.findById(parentId.getId()))
                        .get()
                        .returns(2L, Entity::getEthereumNonce));
    }

    @Test
    void contractUpdateWithHooksSequentialOperations() {
        // Step 1: Create an contract with hooks
        var contractUpdateWithHookDetails = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.clearHookIdsToDelete())
                .build();
        parseRecordItemAndCommit(contractUpdateWithHookDetails);

        var ownerId = contractUpdateWithHookDetails
                .getTransactionRecord()
                .getReceipt()
                .getContractID();
        var contractUpdateBody =
                contractUpdateWithHookDetails.getTransactionBody().getContractUpdateInstance();
        var hookCreationDetails = contractUpdateBody.getHookCreationDetails(0);

        // Extract hook details from the transaction
        var hookId = hookCreationDetails.getHookId();
        var contractId =
                EntityId.of(hookCreationDetails.getLambdaEvmHook().getSpec().getContractId());
        var adminKey = hookCreationDetails.getAdminKey();

        // Assert after creation
        final var hookOptional = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(ownerId).getId()));
        assertAll(
                () -> assertTrue(hookOptional.isPresent(), "Hook should exist after creation"),
                () -> assertFalse(hookOptional.get().getDeleted(), "Hook should not be deleted after creation"),
                () -> assertEquals(1, hookRepository.count(), "Should have 1 hook after creation"));
        var dbHook = hookOptional.get();
        assertAll(
                () -> assertEquals(hookId, dbHook.getHookId()),
                () -> assertEquals(contractId, dbHook.getContractId()),
                () -> assertArrayEquals(adminKey.toByteArray(), dbHook.getAdminKey()),
                () -> assertEquals(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, dbHook.getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, dbHook.getType()),
                () -> assertEquals(EntityId.of(ownerId).getId(), dbHook.getOwnerId()),
                () -> assertEquals(contractUpdateWithHookDetails.getConsensusTimestamp(), dbHook.getCreatedTimestamp()),
                () -> assertFalse(dbHook.getDeleted()));

        // Step 2: Delete and create hook in same transaction
        var deletionAndCreationRecordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.setContractID(ownerId))
                .receipt(r -> r.setContractID(ownerId))
                .build();
        parseRecordItemAndCommit(deletionAndCreationRecordItem);

        // Assert after deletion and creation in same transaction
        final var hookOptional2 = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(ownerId).getId()));
        assertAll(
                () -> assertTrue(hookOptional2.isPresent(), "Hook should exist after deletion and creation"),
                () -> assertFalse(hookOptional2.get().getDeleted(), "Hook should not be deleted after recreation"),
                () -> assertEquals(
                        deletionAndCreationRecordItem.getConsensusTimestamp(),
                        hookOptional2.get().getCreatedTimestamp(),
                        "Hook should have creation timestamp from the transaction"),
                () -> assertEquals(
                        1, hookRepository.count(), "Should have 1 hook record (upsert merges delete and create)"));

        // Step 3: Final deletion
        var finalDeleteRecordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.setContractID(ownerId))
                .receipt(r -> r.setContractID(ownerId))
                .transactionBody(b -> b.clearHookCreationDetails())
                .build();
        parseRecordItemAndCommit(finalDeleteRecordItem);

        // Assert after final deletion
        final var hookOptional3 = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(ownerId).getId()));
        assertAll(
                () -> assertTrue(hookOptional3.isPresent(), "Hook should still exist after final soft delete"),
                () -> assertTrue(
                        hookOptional3.get().getDeleted(), "Hook should be marked as deleted after final deletion"),
                () -> assertEquals(1, hookRepository.count(), "Hook count should remain same for soft delete"),
                () -> assertTrue(hookOptional3.get().getDeleted()));
    }

    @Test
    void contractCallChildNonce() {
        var childContractID = domainBuilder.entityNum(1001).toContractID();

        var setupResult = setupContract(CONTRACT_ID, ContractIdType.PLAIN, true, true);
        var parentId = setupResult.entity.toEntityId();
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .receipt(r -> r.setContractID(CONTRACT_ID))
                .transactionBody(b -> b.setContractID(setupResult.protoContractId))
                .record(r -> r.clearContractCallResult()
                        .setContractCallResult(recordItemBuilder
                                .contractFunctionResult(CONTRACT_ID)
                                .clearContractNonces()
                                .addContractNonces(ContractNonceInfo.newBuilder()
                                        .setContractId(CONTRACT_ID)
                                        .setNonce(2))
                                .addContractNonces(ContractNonceInfo.newBuilder()
                                        .setContractId(childContractID)
                                        .setNonce(1))))
                .build();
        var txnRecord = parentRecordItem.getTransactionRecord();
        var transactionBody = parentRecordItem.getTransactionBody().getContractCall();
        EntityId createdId = EntityId.of(childContractID);
        var childRecordItem = recordItemBuilder
                .contractCreate(childContractID)
                .record(r -> r.setTransactionID(
                                txnRecord.getTransactionID().toBuilder().setNonce(1))
                        .setParentConsensusTimestamp(txnRecord.getConsensusTimestamp())
                        .setContractCreateResult(
                                r.getContractCreateResult().toBuilder().clearContractNonces()))
                .build();

        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(3, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(8, cryptoTransferRepository.count()),
                () -> assertContractCallResult(transactionBody, txnRecord),
                () -> assertThat(entityRepository.findById(createdId.getId()))
                        .get()
                        .returns(1L, Entity::getEthereumNonce),
                () -> assertThat(entityRepository.findById(parentId.getId()))
                        .get()
                        .returns(2L, Entity::getEthereumNonce));
    }

    // Issue #5637 caused by an invalid receipt.ContractID sent by consensus nodes in testnet.
    @Test
    void contractCreateWithInvalidId() {
        var invalidId = ContractID.newBuilder()
                .setShardNum(-382413634L)
                .setRealmNum(-4713217343126473096L)
                .setContractNum(-9016639978277801310L)
                .build();
        var recordItem = recordItemBuilder
                .contractCreate()
                .receipt(r -> r.setContractID(invalidId))
                .record(r -> r.getContractCreateResultBuilder().setContractID(invalidId))
                .sidecarRecords(List::clear)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertContractStateChanges(recordItem),
                () -> assertThat(transactionRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(null, org.hiero.mirror.common.domain.transaction.Transaction::getEntityId),
                () -> assertThat(contractResultRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(0L, ContractResult::getContractId));
        assertEntityTransactions(recordItem);
        //        assertThat(entityTransactionRepository.findAll()).isEmpty();
    }

    @SuppressWarnings("deprecation")
    @Test
    void contractCreateWithEvmAddress() {
        // no child tx, creates a single contract with evm address set
        byte[] evmAddress = domainBuilder.evmAddress();
        RecordItem recordItem = recordItemBuilder
                .contractCreate(CONTRACT_ID)
                .transactionBody(t -> t.setDeclineReward(false).setStakedNodeId(1L))
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs()
                        .addCreatedContractIDs(CONTRACT_ID)
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(evmAddress)))))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertContractEntity(recordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertContractCreateResult(transactionBody, txnRecord),
                () -> assertContractStateChanges(recordItem),
                () -> assertEntityTransactions(recordItem));
    }

    @SuppressWarnings("deprecation")
    @Test
    void contractCreateWithEvmAddressAndChildCreate() {
        // given contractCreate with child contractCreate
        var parentEvmAddress = domainBuilder.evmAddress();
        var parentRecordItem = recordItemBuilder
                .contractCreate()
                .record(r -> r.setContractCreateResult(r.getContractCreateResultBuilder()
                        .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(parentEvmAddress)))))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .sidecarRecords(List::removeFirst)
                .build();

        var contractCreateResult = parentRecordItem.getTransactionRecord().getContractCreateResult();
        var childContractId = contractCreateResult.getCreatedContractIDsList().stream()
                .filter(c -> !c.equals(contractCreateResult.getContractID()))
                .findFirst()
                .get();
        var childEvmAddress = domainBuilder.evmAddress();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childTransactionId = parentRecordItem.getTransactionRecord().getTransactionID().toBuilder()
                .setNonce(1);
        var childRecordItem = recordItemBuilder
                .contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setTransactionID(childTransactionId)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress)))))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .sidecarRecords(s -> s.remove(0))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        // then
        var parentTransactionBody = parentRecordItem.getTransactionBody().getContractCreateInstance();
        var childTransactionBody = childRecordItem.getTransactionBody().getContractCreateInstance();
        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(8, cryptoTransferRepository.count()),
                () -> assertContractEntity(parentRecordItem),
                () -> assertContractEntity(childRecordItem),
                () -> assertThat(contractResultRepository.findAll()).hasSize(2),
                () -> assertContractCreateResult(parentTransactionBody, parentRecordItem.getTransactionRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getTransactionRecord()),
                () -> assertContractStateChanges(parentRecordItem),
                () -> assertContractStateChanges(childRecordItem),
                () -> assertEntityTransactions(parentRecordItem, childRecordItem));
    }

    @Test
    void contractCreateFailedWithResult() {
        var recordItem = recordItemBuilder
                .contractCreate()
                .record(r -> r.setContractCreateResult(ContractFunctionResult.getDefaultInstance()))
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .sidecarRecords(List::clear)
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, txnRecord),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCreateFailedWithoutResult() {
        RecordItem recordItem = recordItemBuilder
                .contractCreate()
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertFailedContractCreate(transactionBody, txnRecord),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCreateDoNotPersist() {
        entityProperties.getPersist().setContracts(false);

        var recordItem = recordItemBuilder.contractCreate().build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();
        var entityIds = List.of(
                // still have contract id since it's the transaction's main entity
                EntityId.of(txnRecord.getReceipt().getContractID()),
                EntityId.of(transactionBody.getNodeAccountID()),
                recordItem.getPayerAccountId());
        var expectedEntityTransactions = toEntityTransactions(
                recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion());

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertFalse(
                        getContractResult(txnRecord.getConsensusTimestamp()).isPresent()));
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions.values());
    }

    @ParameterizedTest
    @CsvSource({"PLAIN, 5005, 5005", "PARSABLE_EVM, 0, 0", "CREATE2_EVM, , 5002"})
    void contractUpdateAllToExisting(
            ContractIdType contractIdType, Long newAutoRenewAccount, Long expectedAutoRenewAccount) {
        // first create the contract
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true, c -> {
            c.obtainerId(null).declineReward(true).stakedAccountId(1L);
            if (newAutoRenewAccount == null) {
                c.autoRenewAccountId(expectedAutoRenewAccount);
            }
        });
        Entity entity = setupResult.entity;

        // now update
        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true, b -> {
            b.setDeclineReward(BoolValue.of(false));
            if (newAutoRenewAccount != null) {
                b.getAutoRenewAccountIdBuilder().setAccountNum(newAutoRenewAccount);
            }
        });
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        var recordItem = RecordItem.builder()
                .hapiVersion(RecordFile.HAPI_VERSION_0_27_0)
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractEntity(contractUpdateTransactionBody, txnRecord.getConsensusTimestamp())
                        .returns(expectedAutoRenewAccount, Entity::getAutoRenewAccountId)
                        .returns(entity.getCreatedTimestamp(), Entity::getCreatedTimestamp)
                        .returns(false, Entity::getDeclineReward));
    }

    @Test
    void contractUpdateAllWithMemoToExisting() {
        // first create the contract
        var contractId = EntityId.of(CONTRACT_ID);
        var contract = domainBuilder
                .entity(contractId)
                .customize(c -> c.obtainerId(null).stakedNodeId(1L).type(CONTRACT))
                .persist();

        // now update
        var transaction = contractUpdateAllTransaction(false);
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        var contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(contractId),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractEntity(contractUpdateTransactionBody, txnRecord.getConsensusTimestamp())
                        .returns(contract.getCreatedTimestamp(), Entity::getCreatedTimestamp));
    }

    @ParameterizedTest
    @EnumSource(
            value = ContractIdType.class,
            names = {"PLAIN", "PARSABLE_EVM"})
    void contractUpdateAllToNew(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false, false, c -> c.obtainerId(null));

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractEntity(contractUpdateTransactionBody, txnRecord.getConsensusTimestamp())
                        .returns(null, Entity::getCreatedTimestamp));
    }

    @Test
    void contractUpdateAllToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.entity.toEntityId()));
    }

    @Test
    void contractUpdateAllWithMemoToNew() {
        Transaction transaction = contractUpdateAllTransaction(false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.UPDATE);
        ContractUpdateTransactionBody contractUpdateTransactionBody = transactionBody.getContractUpdateInstance();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractEntity(contractUpdateTransactionBody, txnRecord.getConsensusTimestamp())
                        .returns(null, Entity::getCreatedTimestamp));
    }

    @ParameterizedTest
    @EnumSource(value = ContractIdType.class)
    void contractUpdateAllToExistingInvalidTransaction(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        Transaction transaction = contractUpdateAllTransaction(setupResult.protoContractId, true);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(
                transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE, ContractTransactionType.UPDATE);
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractStateChanges(recordItem),
                () -> assertThat(entityRepository.findAll()).containsExactly(setupResult.entity));
    }

    @Test
    void contractUpdateSidecarMigrationNoExisting() {
        // given
        var runtimeBytecode = new byte[] {0, 1, 2};
        var contractBytecode = ContractBytecode.newBuilder().setRuntimeBytecode(fromBytes(runtimeBytecode));
        var sidecar = TransactionSidecarRecord.newBuilder().setMigration(true).setBytecode(contractBytecode);
        var recordItem = recordItemBuilder
                .contractUpdate()
                .sidecarRecords(s -> s.add(sidecar))
                .build();
        var entityId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        softly.assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entityId.getId(), Entity::getId)
                .returns(entityId.getNum(), Entity::getNum)
                .returns(entityId.getRealm(), Entity::getRealm)
                .returns(entityId.getShard(), Entity::getShard)
                .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                .returns(null, Entity::getTimestampUpper)
                .returns(CONTRACT, Entity::getType);
        softly.assertThat(entityHistoryRepository.count()).isZero();
        softly.assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entityId.getId(), Contract::getId)
                .returns(runtimeBytecode, Contract::getRuntimeBytecode);
    }

    @Test
    void contractUpdateSidecarMigrationExisting() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).type(ACCOUNT))
                .persist();
        var contract =
                domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        var contractId = TestUtils.toContractId(entity);
        var runtimeBytecode = new byte[] {0, 1, 2};
        var contractBytecode = ContractBytecode.newBuilder().setRuntimeBytecode(fromBytes(runtimeBytecode));
        var sidecar = TransactionSidecarRecord.newBuilder().setMigration(true).setBytecode(contractBytecode);
        var recordItem = recordItemBuilder
                .contractUpdate()
                .sidecarRecords(s -> s.add(sidecar))
                .receipt(r -> r.setContractID(contractId))
                .transactionBody(b -> b.setContractID(contractId))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        softly.assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entity.getId(), Entity::getId)
                .returns(entity.getNum(), Entity::getNum)
                .returns(entity.getRealm(), Entity::getRealm)
                .returns(entity.getShard(), Entity::getShard)
                .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                .returns(null, Entity::getTimestampUpper)
                .returns(CONTRACT, Entity::getType);
        softly.assertThat(entityHistoryRepository.findAll())
                .hasSize(1)
                .extracting(e -> e.getType())
                .containsOnly(CONTRACT);
        softly.assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(contract.getId(), Contract::getId)
                .returns(runtimeBytecode, Contract::getRuntimeBytecode);
    }

    @ParameterizedTest
    @CsvSource({"PLAIN, false", "PARSABLE_EVM,true", "CREATE2_EVM,false"})
    void contractDeleteToExisting(ContractIdType contractIdType, boolean permanentRemoval) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, permanentRemoval);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        Entity dbContractEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(dbContractEntity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(EntityId.of(PAYER), Entity::getObtainerId)
                        .returns(permanentRemoval, Entity::getPermanentRemoval)
                        .returns(Range.atLeast(recordItem.getConsensusTimestamp()), Entity::getTimestampRange));
    }

    @ParameterizedTest
    @EnumSource(
            value = ContractIdType.class,
            names = {"PLAIN", "PARSABLE_EVM"})
    void contractDeleteToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work for PLAIN and PARSABLE_EVM
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);
        Entity entity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(entity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                        .returns(null, Entity::getAutoRenewPeriod)
                        .returns(null, Entity::getExpirationTimestamp)
                        .returns(null, Entity::getKey)
                        .returns(EntityId.of(PAYER), Entity::getObtainerId)
                        .returns(null, Entity::getProxyAccountId));
    }

    @Test
    void contractDeleteToNewCreate2EvmAddress() {
        SetupResult setupResult = setupContract(CONTRACT_ID, ContractIdType.CREATE2_EVM, false, false);

        Transaction transaction = contractDeleteTransaction(setupResult.protoContractId, false);
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.DELETE);
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(setupResult.entity.toEntityId()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(dbTransaction.getEntityId()).isEqualTo(setupResult.entity.toEntityId()));
    }

    @Test
    void contractDeleteToNewInvalidTransaction() {
        Transaction transaction = contractDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(
                transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE, ContractTransactionType.DELETE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(transactionBody.getContractDeleteInstance().getContractID())
                        .isNotNull());
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToExisting(ContractIdType contractIdType) {
        SetupResult setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);

        var recordItem = recordItemBuilder.contractCall(CONTRACT_ID).build();
        var transactionBody = recordItem.getTransactionBody();
        var txnRecord = recordItem.getTransactionRecord();
        var contractCallTransactionBody = transactionBody.getContractCall();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractCallResult(contractCallTransactionBody, txnRecord),
                () -> assertContractStateChanges(recordItem),
                () -> assertContractAction(recordItem),
                () -> assertThat(entityRepository.findAll()).contains(setupResult.entity),
                () -> assertEntityTransactions(recordItem));
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToExistingWithChildContractCreate(ContractIdType contractIdType) {
        // given contractCall with child contractCreate
        var setupResult = setupContract(CONTRACT_ID, contractIdType, true, true);
        var parentId = setupResult.entity.toEntityId();

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .receipt(r -> r.setContractID(CONTRACT_ID))
                .transactionBody(b -> b.setContractID(setupResult.protoContractId))
                .record(r -> r.clearContractCallResult()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)))
                .sidecarRecords(s -> s.remove(0))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .build();

        var childEvmAddress = domainBuilder.evmAddress();
        var txnRecord = parentRecordItem.getTransactionRecord();
        var childConsensusTimestamp = TestUtils.toTimestamp(parentRecordItem.getConsensusTimestamp() + 1);
        var childContractId = txnRecord.getContractCallResult().getCreatedContractIDs(0);
        var childTransactionId =
                txnRecord.getTransactionID().toBuilder().setNonce(1).build();
        var childRecordItem = recordItemBuilder
                .contractCreate(childContractId)
                .record(r -> r.setConsensusTimestamp(childConsensusTimestamp)
                        .setContractCreateResult(r.getContractCreateResultBuilder()
                                .clearCreatedContractIDs()
                                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(childEvmAddress))))
                        .setTransactionID(childTransactionId))
                .sidecarRecords(s -> s.remove(0))
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(parentRecordItem, childRecordItem));

        // then
        var parentTransactionBody = parentRecordItem.getTransactionBody().getContractCall();
        var childTransactionBody = childRecordItem.getTransactionBody().getContractCreateInstance();
        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(2, contractRepository.count()),
                () -> assertEquals(2, contractResultRepository.count()),
                () -> assertEquals(8, cryptoTransferRepository.count()),
                () -> assertEntities(parentId, EntityId.of(childContractId)),
                () -> assertTransactionAndRecord(
                        parentRecordItem.getTransactionBody(), parentRecordItem.getTransactionRecord()),
                () -> assertTransactionAndRecord(
                        childRecordItem.getTransactionBody(), childRecordItem.getTransactionRecord()),
                () -> assertThat(entityRepository.findAll()).contains(setupResult.entity),
                () -> assertCreatedContract(childRecordItem),
                () -> assertContractCallResult(parentTransactionBody, parentRecordItem.getTransactionRecord()),
                () -> assertContractCreateResult(childTransactionBody, childRecordItem.getTransactionRecord()),
                () -> assertContractStateChanges(parentRecordItem),
                () -> assertContractStateChanges(childRecordItem),
                () -> assertEntityTransactions(parentRecordItem, childRecordItem));
    }

    @ParameterizedTest
    @EnumSource(ContractIdType.class)
    void contractCallToNew(ContractIdType contractIdType) {
        // The contract is not in db, it should still work. Note for the create2 evm address,
        // ContractCallTransactionHandler will get the correct plain contractId from the transaction record instead
        // only cache the create2 evm address to verify it later
        var setupResult =
                setupContract(CONTRACT_ID, contractIdType, false, contractIdType == ContractIdType.CREATE2_EVM);

        var transaction = contractCallTransaction(setupResult.protoContractId);
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.CALL);
        var contractCallTransactionBody = transactionBody.getContractCall();
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(EntityId.of(CREATED_CONTRACT_ID)),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertContractCallResult(contractCallTransactionBody, txnRecord),
                () -> assertContractStateChanges(recordItem),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCallTokenPrecompiles() {
        // given
        var recordItemCall = recordItemBuilder.contractCall().build();
        parseRecordItemAndCommit(recordItemCall);

        var parentConsensusTimestamp = recordItemCall.getTransactionRecord().getConsensusTimestamp();
        var parentTransactionId = recordItemCall.getTransactionBody().getTransactionID();
        var childTransactionId = parentTransactionId.toBuilder().setNonce(1).build();
        var payerAccount = recordItemCall.getPayerAccountId();
        var validStart = DomainUtils.timeStampInNanos(parentTransactionId.getTransactionValidStart());
        var recordItemMint = recordItemBuilder
                .tokenMint(TokenType.FUNGIBLE_COMMON)
                .transactionBodyWrapper(b -> b.setTransactionID(childTransactionId))
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .build();
        var tokenId =
                EntityId.of(recordItemMint.getTransactionBody().getTokenMint().getToken());
        var expectedEntityTransactions = Stream.of(
                        getExpectedEntityTransactions(recordItemCall).stream(),
                        getExpectedEntityTransactions(recordItemMint).stream(),
                        Stream.of(toEntityTransaction(tokenId, recordItemMint)))
                .flatMap(Function.identity())
                .collect(Collectors.toSet());

        // when
        parseRecordItemAndCommit(recordItemMint);

        // then
        assertEquals(2, transactionRepository.count());
        assertThat(transactionRepository.findById(recordItemCall.getConsensusTimestamp()))
                .get()
                .returns(payerAccount, org.hiero.mirror.common.domain.transaction.Transaction::getPayerAccountId)
                .returns(validStart, org.hiero.mirror.common.domain.transaction.Transaction::getValidStartNs)
                .returns(0, org.hiero.mirror.common.domain.transaction.Transaction::getNonce)
                .returns(null, org.hiero.mirror.common.domain.transaction.Transaction::getParentConsensusTimestamp);

        assertThat(transactionRepository.findById(recordItemMint.getConsensusTimestamp()))
                .get()
                .returns(payerAccount, org.hiero.mirror.common.domain.transaction.Transaction::getPayerAccountId)
                .returns(validStart, org.hiero.mirror.common.domain.transaction.Transaction::getValidStartNs)
                .returns(1, org.hiero.mirror.common.domain.transaction.Transaction::getNonce)
                .returns(
                        recordItemCall.getConsensusTimestamp(),
                        org.hiero.mirror.common.domain.transaction.Transaction::getParentConsensusTimestamp);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @Test
    void contractCallFailedWithResult() {
        RecordItem recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCreateResult(ContractFunctionResult.getDefaultInstance()))
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertFailedContractCallTransaction(transactionBody, txnRecord),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCallFailedWithoutResult() {
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(
                        transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE, ContractTransactionType.CALL)
                .toBuilder()
                .clearContractCallResult()
                .build();
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertFailedContractCallTransaction(transactionBody, txnRecord),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCallDoNotPersist() {
        entityProperties.getPersist().setContracts(false);
        Transaction transaction = contractCallTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = getContractTransactionRecord(transactionBody, ContractTransactionType.CALL);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
    }

    // Test for bad entity id in a failed transaction
    @Test
    void contractCallBadContractId() {
        var badContractId = ContractID.newBuilder().setContractNum(-1L).build();
        var recordItem = recordItemBuilder
                .contractCall(badContractId)
                .record(r -> r.clearContractCallResult())
                .sidecarRecords(s -> s.clear())
                .status(ResponseCodeEnum.INVALID_CONTRACT_ID)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.INVALID_CONTRACT_ID))
                .build();
        var txnRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody();

        parseRecordItemAndCommit(recordItem);

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEntities(),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEntityTransactions(recordItem));
    }

    @Test
    void contractCreateWithHooks() {
        // given
        var recordItem = recordItemBuilder.contractCreate().build();

        var ownerId = recordItem.getTransactionRecord().getReceipt().getContractID();
        var contractCreateBody = recordItem.getTransactionBody().getContractCreateInstance();
        var hookCreationDetails = contractCreateBody.getHookCreationDetails(0);

        // Extract hook details from the transaction
        var hookId = hookCreationDetails.getHookId();
        var contractId =
                EntityId.of(hookCreationDetails.getLambdaEvmHook().getSpec().getContractId());
        var adminKey = hookCreationDetails.getAdminKey();

        // when
        parseRecordItemAndCommit(recordItem);

        // Find and verify hook from database
        var hookOptional = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(ownerId).getId()));
        assertAll(
                () -> assertEntityTransactions(recordItem),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()));

        var dbHook = hookOptional.get();
        assertAll(
                () -> assertEquals(hookId, dbHook.getHookId()),
                () -> assertEquals(contractId, dbHook.getContractId()),
                () -> assertArrayEquals(adminKey.toByteArray(), dbHook.getAdminKey()),
                () -> assertEquals(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, dbHook.getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, dbHook.getType()),
                () -> assertEquals(EntityId.of(ownerId).getId(), dbHook.getOwnerId()),
                () -> assertEquals(recordItem.getConsensusTimestamp(), dbHook.getCreatedTimestamp()),
                () -> assertFalse(dbHook.getDeleted()));
    }

    @Test
    void topLevelRecordFiles() {
        var recordItem1 = recordItemBuilder
                .contractCall()
                .record(r -> {
                    r.getTransactionIDBuilder().setNonce(0);
                })
                .build();

        var recordItem2 = recordItemBuilder
                .contractCall()
                .record(r -> {
                    r.getTransactionIDBuilder().setNonce(1).setScheduled(false);
                })
                .build();

        var recordItem3 = recordItemBuilder
                .contractCall()
                .record(r -> {
                    r.getTransactionIDBuilder().setNonce(1).setScheduled(true);
                })
                .build();

        assertThat(recordItem1.isTopLevel()).isTrue();
        assertThat(recordItem2.isTopLevel()).isTrue();
        assertThat(recordItem3.isTopLevel()).isTrue();
    }

    @Test
    void notTopLevelRecordFiles() {
        var recordItem1 = recordItemBuilder
                .contractCall()
                .record(r -> {
                    r.getTransactionIDBuilder().setNonce(1).setScheduled(false);
                    r.setParentConsensusTimestamp(Timestamp.newBuilder()
                            .setSeconds(1499853)
                            .setNanos(0)
                            .build());
                })
                .build();

        var recordItem2 = recordItemBuilder
                .contractCall()
                .record(r -> {
                    r.getTransactionIDBuilder().setNonce(1).setScheduled(false);
                    r.setParentConsensusTimestamp(Timestamp.newBuilder()
                            .setSeconds(0)
                            .setNanos(190024888)
                            .build());
                })
                .build();

        assertThat(recordItem1.isTopLevel()).isFalse();
        assertThat(recordItem2.isTopLevel()).isFalse();
    }

    @Test
    void systemFileUpdateIsTopLevel() {
        var recordItem = recordItemBuilder
                .fileUpdate()
                .record(r -> r.getTransactionIDBuilder()
                        .setNonce(7)
                        .setScheduled(false)
                        .setAccountID(AccountID.newBuilder().setAccountNum(50)))
                .build();

        assertThat(recordItem.isTopLevel()).isTrue();
    }

    private void assertFailedContractCreate(TransactionBody transactionBody, TransactionRecord txnRecord) {
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        var contractCreateBody = transactionBody.getContractCreateInstance();
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(contractCreateBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertPartialContractCreateResult(transactionBody.getContractCreateInstance(), txnRecord));
    }

    private void assertFailedContractCallTransaction(TransactionBody transactionBody, TransactionRecord txnRecord) {
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        var contractCallBody = transactionBody.getContractCall();
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertThat(dbTransaction.getEntityId()).isNotNull(),
                () -> assertEquals(EntityId.of(contractCallBody.getContractID()), dbTransaction.getEntityId()),
                () -> assertPartialContractCallResult(contractCallBody, txnRecord));
    }

    @SuppressWarnings("deprecation")
    private void assertContractEntity(RecordItem recordItem) {
        long createdTimestamp = recordItem.getConsensusTimestamp();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var adminKey = transactionBody.getAdminKey().toByteArray();
        var transaction = transactionRepository.findById(createdTimestamp).get();
        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress()
                ? DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue())
                : null;
        EntityId entityId = transaction.getEntityId();
        Entity entity = getEntity(entityId);
        Long expectedAutoRenewAccountId = transactionBody.hasAutoRenewAccountId()
                ? EntityId.of(transactionBody.getAutoRenewAccountId()).getId()
                : null;
        EntityId expectedFileId = transactionBody.hasFileID() ? EntityId.of(transactionBody.getFileID()) : null;
        ContractBytecode sidecarBytecode = null;
        var sidecarRecords = recordItem.getSidecarRecords();
        var contractId = contractCreateResult.getContractID();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasBytecode()
                    && contractId.equals(sidecarRecord.getBytecode().getContractId())) {
                sidecarBytecode = sidecarRecord.getBytecode();
                break;
            }
        }
        var initcode = !transactionBody.getInitcode().isEmpty()
                ? transactionBody.getInitcode()
                : (sidecarBytecode != null ? sidecarBytecode.getInitcode() : null);
        byte[] expectedInitcode = initcode != null ? DomainUtils.toBytes(initcode) : null;

        assertThat(transaction).isNotNull().returns(transactionBody.getInitialBalance(), t -> t.getInitialBalance());

        assertThat(entity)
                .isNotNull()
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(createdTimestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(evmAddress, Entity::getEvmAddress)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(entityId.getId(), Entity::getId)
                .returns(adminKey, Entity::getKey)
                .returns(transactionBody.getMaxAutomaticTokenAssociations(), Entity::getMaxAutomaticTokenAssociations)
                .returns(transactionBody.getMemo(), Entity::getMemo)
                .returns(createdTimestamp, Entity::getTimestampLower)
                .returns(null, Entity::getObtainerId)
                .returns(EntityId.of(transactionBody.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Entity::getPublicKey)
                .returns(CONTRACT, Entity::getType);

        assertThat(contractRepository.findById(entity.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId)
                .returns(expectedInitcode, Contract::getInitcode);

        if (entityProperties.getPersist().isContracts()) {
            assertCreatedContract(recordItem);
        }
    }

    private void assertCreatedContract(RecordItem recordItem) {
        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        byte[] evmAddress = contractCreateResult.hasEvmAddress()
                ? DomainUtils.toBytes(contractCreateResult.getEvmAddress().getValue())
                : null;
        EntityId createdId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var contractAssert = assertThat(entityRepository.findById(createdId.getId()))
                .get()
                .returns(recordItem.getConsensusTimestamp(), Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(evmAddress, Entity::getEvmAddress)
                .returns(createdId.getId(), Entity::getId)
                .returns(recordItem.getConsensusTimestamp(), Entity::getTimestampLower)
                .returns(createdId.getNum(), Entity::getNum)
                .returns(createdId.getShard(), Entity::getShard)
                .returns(CONTRACT, Entity::getType);

        var contractCreateInstance = recordItem.getTransactionBody().getContractCreateInstance();
        contractAssert.returns(contractCreateInstance.getDeclineReward(), Entity::getDeclineReward);

        if (contractCreateInstance.getStakedIdCase() == ContractCreateTransactionBody.StakedIdCase.STAKEDID_NOT_SET) {
            return;
        }

        contractAssert.returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);

        if (contractCreateInstance.hasStakedAccountId()) {
            var accountId = EntityId.of(contractCreateInstance.getStakedAccountId());
            contractAssert
                    .returns(accountId.getId(), Entity::getStakedAccountId)
                    .returns(-1L, Entity::getStakedNodeId);
        } else {
            contractAssert
                    .returns(contractCreateInstance.getStakedNodeId(), Entity::getStakedNodeId)
                    .returns(null, Entity::getStakedAccountId);
        }
    }

    private void assertContractAction(RecordItem recordItem) {
        int count = 0;
        var repositoryActions = assertThat(contractActionRepository.findAll());

        var contractActions = new ArrayList<com.hedera.services.stream.proto.ContractAction>();
        var sidecarRecords = recordItem.getSidecarRecords();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasActions()) {
                var actions = sidecarRecord.getActions();
                for (int j = 0; j < actions.getContractActionsCount(); j++) {
                    contractActions.add(actions.getContractActions(j));
                }
            }
        }

        for (var contractAction : contractActions) {
            var caller = contractAction.getCallerCase().equals(CALLING_CONTRACT)
                    ? EntityId.of(contractAction.getCallingContract())
                    : EntityId.of(contractAction.getCallingAccount());

            repositoryActions
                    .filteredOn(c -> c.getConsensusTimestamp() == recordItem.getConsensusTimestamp()
                            && c.getCaller().equals(caller))
                    .hasSize(1)
                    .first()
                    .returns(contractAction.getCallDepth(), ContractAction::getCallDepth)
                    .returns(contractAction.getCallOperationTypeValue(), ContractAction::getCallOperationType)
                    .returns(contractAction.getCallTypeValue(), ContractAction::getCallType)
                    .returns(contractAction.getGas(), ContractAction::getGas)
                    .returns(contractAction.getGasUsed(), ContractAction::getGasUsed)
                    .returns(contractAction.getInput().toByteArray(), ContractAction::getInput)
                    .returns(contractAction.getResultDataCase().getNumber(), ContractAction::getResultDataType)
                    .returns(contractAction.getValue(), ContractAction::getValue)
                    .satisfies(c -> assertThat(c.getCaller()).isNotNull())
                    .satisfies(c -> assertThat(c.getResultData()).isNotEmpty())
                    .satisfiesAnyOf(
                            c -> assertThat(c.getRecipientContract()).isNotNull(),
                            c -> assertThat(c.getRecipientAccount()).isNotNull(),
                            c -> assertThat(c.getRecipientAddress()).isNotEmpty());
            ++count;
        }

        repositoryActions.hasSize(count);
    }

    @SuppressWarnings("deprecation")
    private ObjectAssert<Entity> assertContractEntity(
            ContractUpdateTransactionBody expected, Timestamp consensusTimestamp) {
        Entity entity = getTransactionEntity(consensusTimestamp);
        long updatedTimestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        var adminKey = expected.getAdminKey().toByteArray();
        var expectedMaxAutomaticTokenAssociations = expected.hasMaxAutomaticTokenAssociations()
                ? expected.getMaxAutomaticTokenAssociations().getValue()
                : null;

        var contractAssert = assertThat(entity)
                .isNotNull()
                .returns(expected.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(false, Entity::getDeleted)
                .returns(DomainUtils.timeStampInNanos(expected.getExpirationTime()), Entity::getExpirationTimestamp)
                .returns(adminKey, Entity::getKey)
                .returns(expectedMaxAutomaticTokenAssociations, Entity::getMaxAutomaticTokenAssociations)
                .returns(getMemoFromContractUpdateTransactionBody(expected), Entity::getMemo)
                .returns(updatedTimestamp, Entity::getTimestampLower)
                .returns(null, Entity::getObtainerId)
                .returns(EntityId.of(expected.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(DomainUtils.getPublicKey(adminKey), Entity::getPublicKey)
                .returns(CONTRACT, Entity::getType);

        if (expected.getStakedIdCase() == STAKEDID_NOT_SET) {
            return contractAssert;
        }

        if (expected.hasStakedAccountId()) {
            long expectedAccountId = EntityId.of(expected.getStakedAccountId()).getId();
            contractAssert
                    .returns(expectedAccountId, Entity::getStakedAccountId)
                    .returns(null, Entity::getStakedNodeId)
                    .returns(-1L, Entity::getStakePeriodStart);
        } else {
            contractAssert
                    .returns(expected.getStakedNodeId(), Entity::getStakedNodeId)
                    .returns(null, Entity::getStakedAccountId)
                    .returns(
                            Utility.getEpochDay(DomainUtils.timestampInNanosMax(consensusTimestamp)),
                            Entity::getStakePeriodStart);
        }
        return contractAssert;
    }

    private void assertContractCreateResult(
            ContractCreateTransactionBody transactionBody, TransactionRecord txnRecord) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp());
        TransactionReceipt receipt = txnRecord.getReceipt();
        ContractFunctionResult result = txnRecord.getContractCreateResult();

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(EntityId.of(receipt.getContractID()).getId(), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        if (receipt.getStatus() == ResponseCodeEnum.SUCCESS) {
            contractResult.returns(EntityId.of(receipt.getContractID()).getId(), ContractResult::getContractId);
        }

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult);
    }

    private void assertContractCallResult(ContractCallTransactionBody transactionBody, TransactionRecord txnRecord) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp());
        ContractFunctionResult result = txnRecord.getContractCallResult();

        // get the corresponding entity id from the local cache, fall back to parseContractId if not found.
        ContractID protoContractId = txnRecord.getContractCallResult().getContractID();
        EntityId contractId = contractIds.getOrDefault(protoContractId, parseContractId(protoContractId));

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(contractId.getId(), ContractResult::getContractId)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertContractResult(consensusTimestamp, result, result.getLogInfoList(), contractResult);
    }

    @SuppressWarnings("deprecation")
    private void assertContractResult(
            long consensusTimestamp,
            ContractFunctionResult result,
            List<ContractLoginfo> logInfoList,
            ObjectAssert<ContractResult> contractResult) {
        List<Long> createdContractIds = result.getCreatedContractIDsList().stream()
                .map(x -> EntityId.of(x).getId())
                .toList();

        contractResult
                .returns(result.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(result.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(createdContractIds, ContractResult::getCreatedContractIds)
                .returns(result.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(result.toByteArray(), ContractResult::getFunctionResult)
                .returns(result.getGasUsed(), ContractResult::getGasUsed);

        for (int i = 0; i < logInfoList.size(); i++) {
            int index = i;
            ContractLoginfo logInfo = logInfoList.get(i);

            assertThat(contractLogRepository.findById(new ContractLog.Id(consensusTimestamp, index)))
                    .isPresent()
                    .get()
                    .returns(logInfo.getBloom().toByteArray(), ContractLog::getBloom)
                    .returns(consensusTimestamp, ContractLog::getConsensusTimestamp)
                    .returns(EntityId.of(logInfo.getContractID()), ContractLog::getContractId)
                    .returns(logInfo.getData().toByteArray(), ContractLog::getData)
                    .returns(index, ContractLog::getIndex)
                    .returns(EntityId.of(result.getContractID()), ContractLog::getRootContractId)
                    .returns(Utility.getTopic(logInfo, 0), ContractLog::getTopic0)
                    .returns(Utility.getTopic(logInfo, 1), ContractLog::getTopic1)
                    .returns(Utility.getTopic(logInfo, 2), ContractLog::getTopic2)
                    .returns(Utility.getTopic(logInfo, 3), ContractLog::getTopic3);
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        int count = 0;
        var contractStateChanges = assertThat(contractStateChangeRepository.findAll());

        var sidecarStateChanges = new ArrayList<com.hedera.services.stream.proto.ContractStateChange>();
        var sidecarRecords = recordItem.getSidecarRecords();
        for (var sidecarRecord : sidecarRecords) {
            if (sidecarRecord.hasStateChanges()) {
                var stateChanges = sidecarRecord.getStateChanges();
                for (int j = 0; j < stateChanges.getContractStateChangesCount(); j++) {
                    sidecarStateChanges.add(stateChanges.getContractStateChanges(j));
                }
            }
        }

        for (var contractStateChange : sidecarStateChanges) {
            EntityId contractId = EntityId.of(contractStateChange.getContractId());
            for (var storageChange : contractStateChange.getStorageChangesList()) {
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten()
                        ? storageChange.getValueWritten().getValue().toByteArray()
                        : null;

                contractStateChanges
                        .filteredOn(c -> c.getConsensusTimestamp() == recordItem.getConsensusTimestamp()
                                && c.getContractId() == contractId.getId()
                                && Arrays.equals(c.getSlot(), slot))
                        .hasSize(1)
                        .first()
                        .returns(storageChange.getValueRead().toByteArray(), ContractStateChange::getValueRead)
                        .returns(valueWritten, ContractStateChange::getValueWritten);
                ++count;
            }
        }

        contractStateChanges.hasSize(count);

        assertContractState(sidecarStateChanges, recordItem.getConsensusTimestamp());
    }

    private void assertContractState(
            ArrayList<com.hedera.services.stream.proto.ContractStateChange> sidecarStateChanges,
            long consensusTimestamp) {
        int count = 0;
        var contractStates = assertThat(contractStateRepository.findAll());
        for (var contractStateChange : sidecarStateChanges) {
            EntityId contractId = EntityId.of(contractStateChange.getContractId());
            for (var storageChange : contractStateChange.getStorageChangesList()) {
                byte[] slot = DomainUtils.toBytes(storageChange.getSlot());
                byte[] valueWritten = storageChange.hasValueWritten()
                        ? storageChange.getValueWritten().getValue().toByteArray()
                        : null;

                if (valueWritten != null) {
                    contractStates
                            .filteredOn(c -> c.getModifiedTimestamp() == consensusTimestamp
                                    && c.getContractId() == contractId.getId()
                                    && Arrays.equals(c.getSlot(), slot))
                            .hasSize(1)
                            .first()
                            .returns(DomainUtils.leftPadBytes(slot, 32), ContractState::getSlot);
                    ++count;
                }
            }
        }

        contractStates.hasSize(count);
    }

    private void assertEntityTransactions(RecordItem... recordItems) {
        var expected = Stream.of(recordItems)
                .flatMap(recordItem -> getExpectedEntityTransactions(recordItem).stream())
                .toList();

        assertThat(entityTransactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertPartialContractCreateResult(
            ContractCreateTransactionBody transactionBody, TransactionRecord txnRecord) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp());

        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getInitialBalance(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(toBytes(transactionBody.getConstructorParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertPartialContractResult(contractResult);
    }

    private void assertPartialContractCallResult(
            ContractCallTransactionBody transactionBody, TransactionRecord txnRecord) {
        long consensusTimestamp = DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp());
        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(consensusTimestamp))
                .hasSize(1)
                .first()
                .returns(transactionBody.getAmount(), ContractResult::getAmount)
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(EntityId.of(transactionBody.getContractID()).getId(), ContractResult::getContractId)
                .returns(toBytes(transactionBody.getFunctionParameters()), ContractResult::getFunctionParameters)
                .returns(transactionBody.getGas(), ContractResult::getGasLimit);

        assertPartialContractResult(contractResult);
    }

    private void assertPartialContractResult(ObjectAssert<ContractResult> contractResult) {
        contractResult
                .returns(null, ContractResult::getBloom)
                .returns(null, ContractResult::getCallResult)
                .returns(List.of(), ContractResult::getCreatedContractIds)
                .returns(null, ContractResult::getErrorMessage)
                .returns(null, ContractResult::getFunctionResult)
                .returns(null, ContractResult::getGasUsed);
    }

    private TransactionRecord getContractTransactionRecord(
            TransactionBody transactionBody, ContractTransactionType contractTransactionType) {
        return getContractTransactionRecord(transactionBody, ResponseCodeEnum.SUCCESS, contractTransactionType);
    }

    @SuppressWarnings("deprecation")
    private TransactionRecord getContractTransactionRecord(
            TransactionBody transactionBody, ResponseCodeEnum status, ContractTransactionType contractTransactionType) {
        return buildTransactionRecord(
                recordBuilder -> {
                    recordBuilder.getReceiptBuilder().setContractID(CONTRACT_ID);

                    switch (contractTransactionType) {
                        case CREATE:
                            buildContractFunctionResult(recordBuilder.getContractCreateResultBuilder());
                            break;
                        case CALL:
                            var contractFunctionResult = recordBuilder.getContractCallResultBuilder();
                            buildContractFunctionResult(contractFunctionResult);
                            contractFunctionResult.removeCreatedContractIDs(0);
                            break;
                        default:
                            break;
                    }
                },
                transactionBody,
                status.getNumber());
    }

    private Transaction contractUpdateAllTransaction(boolean setMemoWrapperOrMemo) {
        return contractUpdateAllTransaction(CONTRACT_ID, setMemoWrapperOrMemo);
    }

    private Transaction contractUpdateAllTransaction(ContractID contractId, boolean setMemoWrapperOrMemo) {
        return contractUpdateAllTransaction(contractId, setMemoWrapperOrMemo, b -> {});
    }

    @SuppressWarnings("deprecation")
    private Transaction contractUpdateAllTransaction(
            ContractID contractId,
            boolean setMemoWrapperOrMemo,
            Consumer<ContractUpdateTransactionBody.Builder> customizer) {
        return buildTransaction(builder -> {
            ContractUpdateTransactionBody.Builder contractUpdate = builder.getContractUpdateInstanceBuilder();
            contractUpdate.setAdminKey(keyFromString(KEY));
            contractUpdate.setAutoRenewPeriod(
                    Duration.newBuilder().setSeconds(400).build());
            contractUpdate.setContractID(contractId);
            contractUpdate.setExpirationTime(
                    Timestamp.newBuilder().setSeconds(8000).setNanos(10).build());
            contractUpdate.setFileID(FileID.newBuilder()
                    .setShardNum(0)
                    .setRealmNum(0)
                    .setFileNum(2000)
                    .build());
            contractUpdate.setMaxAutomaticTokenAssociations(Int32Value.of(100));
            if (setMemoWrapperOrMemo) {
                contractUpdate.setMemoWrapper(StringValue.of("contract update memo"));
            } else {
                contractUpdate.setMemo("contract update memo");
            }
            contractUpdate.setProxyAccountID(PROXY_UPDATE);
            customizer.accept(contractUpdate);
        });
    }

    private Transaction contractDeleteTransaction(ContractID contractId, boolean permanentRemoval) {
        return buildTransaction(builder -> builder.getContractDeleteInstanceBuilder()
                .setContractID(contractId)
                .setPermanentRemoval(permanentRemoval)
                .setTransferAccountID(PAYER));
    }

    private Transaction contractDeleteTransaction() {
        return contractDeleteTransaction(CONTRACT_ID, false);
    }

    private Transaction contractCallTransaction() {
        return contractCallTransaction(CONTRACT_ID);
    }

    private Transaction contractCallTransaction(ContractID contractId) {
        return buildTransaction(builder -> {
            ContractCallTransactionBody.Builder contractCall = builder.getContractCallBuilder();
            contractCall.setAmount(88889);
            contractCall.setContractID(contractId);
            contractCall.setFunctionParameters(ByteString.copyFromUtf8("Call Parameters"));
            contractCall.setGas(33333);
        });
    }

    private Optional<ContractResult> getContractResult(Timestamp consensusTimestamp) {
        return contractResultRepository.findById(DomainUtils.timeStampInNanos(consensusTimestamp));
    }

    @SuppressWarnings("deprecation")
    private Collection<EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem) {
        var txnRecord = recordItem.getTransactionRecord();
        boolean isContractCreate = recordItem.getTransactionBody().hasContractCreateInstance();
        boolean isContractCall = recordItem.getTransactionBody().hasContractCall();

        var entityIds = txnRecord.getTransferList().getAccountAmountsList().stream()
                .map(aa -> EntityId.of(aa.getAccountID()))
                .collect(Collectors.toList());
        entityIds.addAll(txnRecord.getTokenTransferListsList().stream()
                .flatMap(tokenTransferList -> Stream.of(
                                Stream.of(EntityId.of(tokenTransferList.getToken())),
                                tokenTransferList.getTransfersList().stream().map(aa -> EntityId.of(aa.getAccountID())),
                                tokenTransferList.getNftTransfersList().stream()
                                        .flatMap(nftTransfer -> Stream.of(
                                                EntityId.of(nftTransfer.getReceiverAccountID()),
                                                EntityId.of(nftTransfer.getSenderAccountID()))))
                        .flatMap(Function.identity()))
                .toList());

        recordItem.getSidecarRecords().forEach(sidecar -> {
            sidecar.getActions().getContractActionsList().forEach(action -> {
                entityIds.add(EntityId.of(action.getCallingAccount()));
                entityIds.add(EntityId.of(action.getCallingContract()));
                entityIds.add(EntityId.of(action.getRecipientAccount()));
                entityIds.add(EntityId.of(action.getRecipientContract()));
            });
            entityIds.add(EntityId.of(sidecar.getBytecode().getContractId()));
            sidecar.getStateChanges()
                    .getContractStateChangesList()
                    .forEach(stateChange -> entityIds.add(EntityId.of(stateChange.getContractId())));
        });

        var contractResult = isContractCreate ? txnRecord.getContractCreateResult() : txnRecord.getContractCallResult();
        var rootContractId = parseContractIdOrEmpty(contractResult.getContractID());
        entityIds.add(rootContractId);

        if (isContractCall) {
            entityIds.add(parseContractIdOrEmpty(
                    recordItem.getTransactionBody().getContractCall().getContractID()));
        }

        if (recordItem.getHapiVersion().isLessThan(HAPI_VERSION_0_23_0)) {
            contractResult.getCreatedContractIDsList().forEach(id -> entityIds.add(EntityId.of(id)));
        }
        contractResult.getLogInfoList().forEach(contractLog -> entityIds.add(EntityId.of(contractLog.getContractID())));
        entityIds.add(EntityId.of(contractResult.getSenderId()));

        entityIds.add(EntityId.of(recordItem.getTransactionBody().getNodeAccountID()));
        entityIds.add(recordItem.getPayerAccountId());

        if (isContractCreate) {
            var body = recordItem.getTransactionBody().getContractCreateInstance();

            if (recordItem.isSuccessful() && !EntityId.isEmpty(rootContractId)) {
                // note root contract id is empty when it's invalid, and there will be no entity transactions created
                // for id fields in the tx body
                entityIds.add(EntityId.of(body.getAutoRenewAccountId()));
                entityIds.add(EntityId.of(body.getFileID()));
                entityIds.add(EntityId.of(body.getProxyAccountID()));
                entityIds.add(EntityId.of(body.getStakedAccountId()));
                List<EntityId> hookEntityIds = body.getHookCreationDetailsList().stream()
                        .map(x -> parseContractId(x.getLambdaEvmHook().getSpec().getContractId()))
                        .toList();
                entityIds.addAll(hookEntityIds);
            }
        }

        return toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values();
    }

    @SuppressWarnings("deprecation")
    private String getMemoFromContractUpdateTransactionBody(ContractUpdateTransactionBody body) {
        switch (body.getMemoFieldCase()) {
            case MEMOWRAPPER:
                return body.getMemoWrapper().getValue();
            case MEMO:
                return body.getMemo();
            default:
                return null;
        }
    }

    private ContractID getContractId(ContractID contractId, byte[] evmAddress) {
        if (evmAddress == null) {
            return contractId;
        }

        return contractId.toBuilder()
                .clearContractNum()
                .setEvmAddress(ByteString.copyFrom(evmAddress))
                .build();
    }

    private byte[] getEvmAddress(ContractIdType contractIdType, EntityId contractId) {
        switch (contractIdType) {
            case PARSABLE_EVM:
                return DomainUtils.toEvmAddress(contractId);
            case CREATE2_EVM:
                return domainBuilder.evmAddress();
            default:
                return null;
        }
    }

    private EntityId parseContractId(ContractID contractId) {
        switch (contractId.getContractCase()) {
            case CONTRACTNUM:
                return EntityId.of(contractId);
            case EVM_ADDRESS:
                ByteBuffer buffer = ByteBuffer.wrap(DomainUtils.toBytes(contractId.getEvmAddress()));
                long shard = buffer.getInt();
                long realm = buffer.getLong();
                long num = buffer.getLong();
                if (shard == contractId.getShardNum() && realm == contractId.getRealmNum()) {
                    return EntityId.of(shard, realm, num);
                }

                // the create2 evm address
                return null;
            default:
                return null;
        }
    }

    private EntityId parseContractIdOrEmpty(ContractID contractId) {
        if (contractId.getContractCase() != ContractID.ContractCase.CONTRACTNUM) {
            return EntityId.EMPTY;
        }

        try {
            return EntityId.of(contractId);
        } catch (Exception e) {
            return EntityId.EMPTY;
        }
    }

    private SetupResult setupContract(
            ContractID contractId, ContractIdType contractIdType, boolean persist, boolean cache) {
        return setupContract(contractId, contractIdType, persist, cache, null);
    }

    private SetupResult setupContract(
            ContractID contractId,
            ContractIdType contractIdType,
            boolean persist,
            boolean cache,
            Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        EntityId entityId = EntityId.of(contractId);
        byte[] evmAddress = getEvmAddress(contractIdType, entityId);
        ContractID protoContractId = getContractId(CONTRACT_ID, evmAddress);
        var builder = domainBuilder.entity().customize(c -> c.evmAddress(evmAddress)
                .id(entityId.getId())
                .num(entityId.getNum())
                .ethereumNonce(1L)
                .type(CONTRACT));
        if (customizer != null) {
            builder.customize(customizer);
        }
        Entity entity = persist ? builder.persist() : builder.get();
        if (cache) {
            contractIds.put(protoContractId, entityId);
        }
        domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        return new SetupResult(entity, protoContractId);
    }

    enum ContractIdType {
        PLAIN,
        PARSABLE_EVM,
        CREATE2_EVM,
    }

    enum ContractTransactionType {
        CREATE,
        CALL,
        UPDATE,
        DELETE
    }

    private record SetupResult(Entity entity, ContractID protoContractId) {}
}
