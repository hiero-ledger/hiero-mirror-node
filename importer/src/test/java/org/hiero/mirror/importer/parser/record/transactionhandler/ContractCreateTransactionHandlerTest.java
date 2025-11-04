// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.service.ContractBytecodeService;
import org.hiero.mirror.importer.service.ContractInitcodeServiceImpl;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.data.util.Version;

final class ContractCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final int DEFAULT_BYTECODE_SIDECAR_INDEX = 2;

    @Mock
    private ContractBytecodeService contractBytecodeService;

    @Mock
    private EVMHookHandler evmHookHandler;

    @Captor
    private ArgumentCaptor<Contract> contracts;

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(Optional.of(defaultEntityId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractCreateTransactionHandler(
                new ContractInitcodeServiceImpl(contractBytecodeService),
                entityIdService,
                entityListener,
                entityProperties,
                evmHookHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected AbstractEntity getExpectedUpdatedEntity() {
        AbstractEntity entity = super.getExpectedUpdatedEntity();
        entity.setBalance(0L);
        entity.setDeclineReward(false);
        entity.setMaxAutomaticTokenAssociations(0);
        return entity;
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setContractID(contractId);
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(
            Descriptors.FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);
        testSpecs.stream().forEach(testSpec -> {
            var consensusTimestamp = testSpec.getRecordItem().getConsensusTimestamp();
            testSpec.getExpected().setBalanceTimestamp(consensusTimestamp);
        });

        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        body = getTransactionBody(body, innerBody);
        byte[] evmAddress = TestUtils.generateRandomByteArray(20);
        var contractCreateResult =
                ContractFunctionResult.newBuilder().setEvmAddress(BytesValue.of(ByteString.copyFrom(evmAddress)));
        var recordBuilder = getDefaultTransactionRecord().setContractCreateResult(contractCreateResult);
        var recordItem = getRecordItem(body, recordBuilder.build());
        AbstractEntity expected = getExpectedUpdatedEntity();
        expected.setBalanceTimestamp(recordItem.getConsensusTimestamp());
        expected.setEvmAddress(evmAddress);
        expected.setMemo("");
        testSpecs.add(UpdateEntityTestSpec.builder()
                .description("create contract entity with evm address in record")
                .expected(expected)
                .recordItem(recordItem)
                .build());

        return testSpecs;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return super.getDefaultTransactionRecord()
                .setContractCreateResult(ContractFunctionResult.newBuilder().addCreatedContractIDs(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return CONTRACT;
    }

    @Test
    void evmHookHandlerCalledWithHookCreationDetails() {
        // given
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId())
                .build();
        var transaction = transaction(recordItem);
        var ownerId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(ownerId.getId()),
                        eq(transactionBody.getHookCreationDetailsList()),
                        eq(List.of()));

        // Verify entity was created
        assertEntity(ownerId, recordItem.getConsensusTimestamp());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void evmHookHandlerNotCalledWhenNoHooks() {
        // given
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearHookCreationDetails())
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler).process(eq(recordItem), anyLong(), eq(List.of()), eq(List.of()));
    }

    @Test
    void updateContractResultEmptyContractCallFunctionParams() {
        ContractResult contractResult = new ContractResult();
        var recordItem = recordItemBuilder.contractCreate().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCreateInstance();
        assertThat(contractResult)
                .returns(transaction.getInitialBalance(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(null, ContractResult::getFailedInitcode)
                .returns(
                        DomainUtils.toBytes(transaction.getConstructorParameters()),
                        ContractResult::getFunctionParameters);
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @Test
    void updateContractResultFailedCreateTransaction() {
        var contractResult = new ContractResult();
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(t -> t.setInitcode(ByteString.copyFrom(new byte[] {9, 8, 7})))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCreateInstance();
        assertThat(contractResult)
                .returns(transaction.getInitialBalance(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(DomainUtils.toBytes(transaction.getInitcode()), ContractResult::getFailedInitcode)
                .returns(
                        DomainUtils.toBytes(transaction.getConstructorParameters()),
                        ContractResult::getFunctionParameters);
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @Test
    void updateContractResultNonContractCallTransaction() {
        ContractResult contractResult = ContractResult.builder().build();
        var recordItem = recordItemBuilder.contractCall().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        assertThat(contractResult)
                .returns(null, ContractResult::getAmount)
                .returns(null, ContractResult::getGasLimit)
                .returns(null, ContractResult::getFailedInitcode)
                .returns(null, ContractResult::getFunctionParameters);
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionSuccessful(boolean blockstream) {
        // given
        var protoContractId = recordItemBuilder.contractId();
        var recordItem = recordItemBuilder
                .contractCreate(protoContractId)
                .recordItem(r -> r.blockstream(blockstream))
                .sidecarRecords(sidecars -> {
                    if (blockstream) {
                        sidecars.get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                                .getBytecodeBuilder()
                                .clearInitcode();
                    }
                })
                .build();
        var contractId = EntityId.of(protoContractId);
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var body = recordItem.getTransactionBody().getContractCreateInstance();
        var key = body.getAdminKey();
        var simpleKey = key.hasEd25519()
                ? key.getEd25519().toByteArray()
                : key.getECDSASecp256K1().toByteArray();
        var autoRenewAccount = body.getAutoRenewAccountId();
        var fileId = EntityId.of(body.getFileID());
        byte[] initCode;
        if (blockstream) {
            initCode = domainBuilder.bytes(1024);
            when(contractBytecodeService.get(fileId)).thenReturn(initCode);
        } else {
            initCode = DomainUtils.toBytes(recordItem
                    .getSidecarRecords()
                    .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                    .getBytecode()
                    .getInitcode());
        }
        when(entityIdService.lookup(autoRenewAccount)).thenReturn(Optional.of(EntityId.of(autoRenewAccount)));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(contractId, timestamp)
                .returns(EntityId.of(autoRenewAccount).getId(), Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress)
                .returns(key.toByteArray(), Entity::getKey)
                .returns(Hex.encodeHexString(simpleKey), Entity::getPublicKey);
        assertContract(contractId).returns(fileId, Contract::getFileId).returns(initCode, Contract::getInitcode);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionMissingKey() {
        // given
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearAdminKey().clearAutoRenewAccountId())
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var key = Key.newBuilder().setContractID(contractId.toContractID()).build();
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(key.toByteArray(), Entity::getKey)
                .returns("", Entity::getPublicKey);
    }

    @Test
    void updateTransactionSuccessfulWithEvmAddressAndInitcode() {
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().clearFileID().setInitcode(recordItemBuilder.bytes(2048)))
                .record(r -> r.getContractCreateResultBuilder().setEvmAddress(recordItemBuilder.evmAddress()))
                .build();
        var contractEntityId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractEntityId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractEntityId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getEvmAddress()).hasSize(20));
        assertContract(contractEntityId).returns(null, Contract::getFileId).satisfies(c -> assertThat(c.getInitcode())
                .hasSize(2048));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccount = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.setAutoRenewAccountId(aliasAccount))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());
        var aliasAccountId = domainBuilder.entityNum(10L);
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);
        expectedEntityTransactions.put(
                aliasAccountId.getId(), TestUtils.toEntityTransaction(aliasAccountId, recordItem));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(contractId, timestamp)
                .returns(aliasAccountId.getId(), Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress);
        assertContract(contractId)
                .returns(
                        EntityId.of(recordItem
                                .getTransactionBody()
                                .getContractCreateInstance()
                                .getFileID()),
                        Contract::getFileId)
                .returns(initCode, Contract::getInitcode);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @ValueSource(ints = {27, 28})
    void updateTransactionStakedAccountId(int minorVersion) {
        // given
        final AccountID accountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, minorVersion, 0)))
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().setDeclineReward(false).setStakedAccountId(accountId))
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(false, Entity::getDeclineReward)
                .returns(accountId.getAccountNum(), Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionStakedNodeId() {
        // given
        var nodeId = 1L;
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().setDeclineReward(true).setStakedNodeId(nodeId))
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(true, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void doNotUpdateTransactionStakedAccountIdBeforeConsensusStaking() {
        // given
        final AccountID accountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 26, 0)))
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().setDeclineReward(false).setStakedAccountId(accountId))
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(null, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakePeriodStart);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionEntityNotFound(EntityId entityId) {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.ofNullable(entityId));
        transactionHandler.updateTransaction(transaction, recordItem);

        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress);
        assertContract(contractId)
                .returns(
                        EntityId.of(recordItem
                                .getTransactionBody()
                                .getContractCreateInstance()
                                .getFileID()),
                        Contract::getFileId)
                .returns(initCode, Contract::getInitcode);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromContractCreateParentNotAChild() {
        // parent item
        var parentRecordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        x -> x.clearFileID().setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.parent(parentRecordItem))
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(null, Contract::getFileId).returns(initCode, Contract::getInitcode);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromContractCreateNoParent() {
        // parent item
        var parentRecordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        x -> x.clearFileID().setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(null, Contract::getFileId).returns(initCode, Contract::getInitcode);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromContractCreateWInitCodeParent() {
        // parent item
        var parentRecordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        x -> x.clearFileID().setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.parent(parentRecordItem))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(null, Contract::getFileId).satisfies(c -> assertThat(c.getInitcode())
                .isNotEmpty());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromContractCreateWFileIDParent() {
        // parent item
        var parentRecordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(x -> x.clearInitcode().setFileID(defaultEntityId.toFileID()))
                .build();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.parent(parentRecordItem))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(initCode, Contract::getInitcode).satisfies(c -> assertThat(c.getFileId())
                .isNotNull());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromEthereumCreateWInitCodeParent() {
        // parent item
        var parentRecordItem = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(Builder::clearCallData)
                .build();

        var ethereumTransaction = domainBuilder
                .ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(null, Contract::getFileId).satisfies(c -> assertThat(c.getInitcode())
                .isNotEmpty());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromEthereumCreateWFileIDParent() {
        // given
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(true).build();

        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem
                .getSidecarRecords()
                .get(DEFAULT_BYTECODE_SIDECAR_INDEX)
                .getBytecode()
                .getInitcode());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(initCode, Contract::getInitcode).satisfies(c -> assertThat(c.getFileId())
                .isNotNull());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateContractFromEthereumCallWCallDataFileParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(false).build();

        var ethereumTransaction = domainBuilder
                .ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(
                        b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp).returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId).returns(null, Contract::getFileId).satisfies(c -> assertThat(c.getInitcode())
                .isNotEmpty());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void migrationBytecodeNotProcessed() {
        var recordItem = recordItemBuilder
                .contractCreate()
                .sidecarRecords(r -> r.get(DEFAULT_BYTECODE_SIDECAR_INDEX).setMigration(true))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var autoRenewAccount =
                recordItem.getTransactionBody().getContractCreateInstance().getAutoRenewAccountId();
        when(entityIdService.lookup(autoRenewAccount)).thenReturn(Optional.of(EntityId.of(autoRenewAccount)));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId).returns(null, Contract::getInitcode).returns(null, Contract::getRuntimeBytecode);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    private ObjectAssert<Contract> assertContract(EntityId contractId) {
        verify(entityListener).onContract(contracts.capture());
        return assertThat(contracts.getAllValues()).hasSize(1).first().returns(contractId.getId(), Contract::getId);
    }

    private ObjectAssert<Entity> assertEntity(EntityId contractId, long timestamp) {
        verify(entityListener).onEntity(entityCaptor.capture());
        return assertThat(entityCaptor.getValue())
                .isNotNull()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(0L, Entity::getBalance)
                .returns(timestamp, Entity::getBalanceTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(contractId.getId(), Entity::getId)
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(contractId.getNum(), Entity::getNum)
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .returns(contractId.getRealm(), Entity::getRealm)
                .returns(contractId.getShard(), Entity::getShard)
                .returns(CONTRACT, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(null, Entity::getObtainerId);
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getContractCreateInstance();
        var autoRenewAccountId =
                !body.getAutoRenewAccountId().hasAlias() ? EntityId.of(body.getAutoRenewAccountId()) : EntityId.EMPTY;
        return getExpectedEntityTransactions(
                recordItem,
                transaction,
                autoRenewAccountId,
                EntityId.of(body.getFileID()),
                EntityId.of(body.getProxyAccountID()),
                EntityId.of(body.getStakedAccountId()));
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
