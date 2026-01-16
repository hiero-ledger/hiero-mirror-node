// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

class ContractUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EVMHookHandler evmHookHandler = mock(EVMHookHandler.class);

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(Optional.of(defaultEntityId));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractUpdateTransactionHandler(entityIdService, entityListener, evmHookHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder().setContractID(defaultEntityId.toContractID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void evmHookHandlerCalledWithHookCreationDetails() {
        // given
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.clearHookIdsToDelete())
                .build();
        var transaction = transaction(recordItem);
        var contractUpdateInstance = recordItem.getTransactionBody().getContractUpdateInstance();
        var ownerId = EntityId.of(contractUpdateInstance.getContractID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(ownerId.getId()),
                        eq(contractUpdateInstance.getHookCreationDetailsList()),
                        eq(List.of()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerCalledWithHookDeletionDetails() {
        // given
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.clearHookCreationDetails())
                .build();
        var transaction = transaction(recordItem);
        var contractUpdateInstance = recordItem.getTransactionBody().getContractUpdateInstance();
        var ownerId = EntityId.of(contractUpdateInstance.getContractID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(ownerId.getId()),
                        eq(contractUpdateInstance.getHookCreationDetailsList()),
                        eq(contractUpdateInstance.getHookIdsToDeleteList()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerCalledWithBothHookCreationAndDeletionDetails() {
        // given
        var recordItem = recordItemBuilder.contractUpdate().build();
        var transaction = transaction(recordItem);
        var contractUpdateInstance = recordItem.getTransactionBody().getContractUpdateInstance();
        var ownerId = EntityId.of(contractUpdateInstance.getContractID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(ownerId.getId()),
                        eq(contractUpdateInstance.getHookCreationDetailsList()),
                        eq(contractUpdateInstance.getHookIdsToDeleteList()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerNotCalledWhenNoHooks() {
        // given
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.clearHookCreationDetails().clearHookIdsToDelete())
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler).process(eq(recordItem), anyLong(), eq(List.of()), eq(List.of()));
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .build();
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractUpdateInstance().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(Optional.of(expectedEntityId));
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccount =
                recordItem.getTransactionBody().getContractUpdateInstance().getAutoRenewAccountId();
        var aliasAccountId = EntityId.of(10L);
        var expectedEntityTransactions = getExpectedEntityTransactions(aliasAccountId, recordItem, transaction);
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse()));
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulWhenEntityTransactionDisabled() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .entityTransactionPredicate(e -> false)
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccount =
                recordItem.getTransactionBody().getContractUpdateInstance().getAutoRenewAccountId();
        var aliasAccountId = EntityId.of(10L);
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse()));
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,28", "100,27"})
    void updateTransactionStakedAccountId(long accountNum, int minorVersion) {
        // Note, the sentinel value '0.0.0' clears the staked account id, in importer, we persist the encoded id '0' to
        // db to indicate there is no staked account id
        AccountID accountId = AccountID.newBuilder().setAccountNum(accountNum).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, minorVersion, 0)))
                .transactionBody(body -> body.setStakedAccountId(accountId).clearDeclineReward())
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(accountNum, Entity::getStakedAccountId)
                .returns(null, Entity::getDeclineReward)
                .returns(-1L, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionDeclineReward(Boolean declineReward) {
        RecordItem withDeclineValueSet = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(body -> body.setDeclineReward(BoolValue.of(declineReward))
                        .clearStakedAccountId()
                        .clearStakedNodeId())
                .build();
        setupForContractUpdateTransactionTest(withDeclineValueSet, t -> assertThat(t)
                .returns(declineReward, Entity::getDeclineReward)
                // since the contract is not being saved in the database,
                // it does not have the default values of -1 for the staking fields.
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .returns(
                        Utility.getEpochDay(withDeclineValueSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, -1})
    void updateTransactionStakedNodeId(Long nodeId) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(body -> body.setStakedNodeId(nodeId))
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(0L, Entity::getStakedAccountId)
                .returns(true, Entity::getDeclineReward)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @Test
    void doNotUpdateTransactionStakedAccountIdBeforeConsensusStaking() {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 26, 0)))
                .transactionBody(body -> body.setStakedNodeId(100))
                .build();
        var contractId = EntityId.of(
                withStakedNodeIdSet.getTransactionRecord().getReceipt().getContractID());
        var timestamp = withStakedNodeIdSet.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccountId = EntityId.of(10L);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(aliasAccountId));
        transactionHandler.updateTransaction(transaction, withStakedNodeIdSet);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(null, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakePeriodStart));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var plainAccountId = domainBuilder.entityNum(10L);
        var aliasProtoAccountId =
                plainAccountId.toAccountID().toBuilder().setAlias(alias).build();
        when(entityIdService.lookup(aliasProtoAccountId)).thenReturn(Optional.of(plainAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(plainAccountId.getId(), Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse()));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(plainAccountId, recordItem, transaction));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionEntityNotFound(EntityId entityId) {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasProtoAccountId = domainBuilder.entityNum(10L).toAccountID().toBuilder()
                .setAlias(alias)
                .build();
        when(entityIdService.lookup(aliasProtoAccountId)).thenReturn(Optional.ofNullable(entityId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedAutoRenewAccountId = entityId == null ? null : entityId.getId();
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse()));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(null, recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountId() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder()
                        .setShardNum(0)
                        .setRealmNum(0)
                        .setAccountNum(0))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(0L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(
                        c -> assertThat(EntityId.isEmpty(c.getProxyAccountId())).isFalse()));
    }

    @Test
    void updateTransactionSuccessfulWithNoUpdate() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(b -> {
                    var contractId = b.getContractID();
                    b.clear().setContractID(contractId);
                })
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(null, Entity::getAutoRenewPeriod)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(null, Entity::getKey)
                .returns(null, Entity::getMaxAutomaticTokenAssociations)
                .returns(null, Entity::getMemo)
                .returns(null, Entity::getProxyAccountId)
                .returns(null, Entity::getPublicKey));
    }

    private void assertContractUpdate(long timestamp, EntityId contractId, Consumer<Entity> extraAssert) {
        verify(entityListener, times(1))
                .onEntity(assertArg(t -> assertAll(
                        () -> assertThat(t)
                                .isNotNull()
                                .returns(null, Entity::getCreatedTimestamp)
                                .returns(false, Entity::getDeleted)
                                .returns(null, Entity::getEvmAddress)
                                .returns(contractId.getId(), Entity::getId)
                                .returns(contractId.getNum(), Entity::getNum)
                                .returns(null, Entity::getObtainerId)
                                .returns(contractId.getRealm(), Entity::getRealm)
                                .returns(contractId.getShard(), Entity::getShard)
                                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                                .returns(CONTRACT, Entity::getType),
                        () -> extraAssert.accept(t))));
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(
            EntityId autoRenewAccountId, RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getContractUpdateInstance();
        if (EntityId.isEmpty(autoRenewAccountId)
                && !body.getAutoRenewAccountId().hasAlias()) {
            autoRenewAccountId = EntityId.of(body.getAutoRenewAccountId());
        }

        return getExpectedEntityTransactions(
                recordItem,
                transaction,
                autoRenewAccountId,
                EntityId.of(body.getStakedAccountId()),
                EntityId.of(body.getProxyAccountID()));
    }

    private void setupForContractUpdateTransactionTest(RecordItem recordItem, Consumer<Entity> extraAssertions) {
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccountId = EntityId.of(10L);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(aliasAccountId));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, extraAssertions);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(aliasAccountId, recordItem, transaction));
    }

    private Transaction transaction(RecordItem recordItem) {
        var contractId = EntityId.of(
                recordItem.getTransactionBody().getContractUpdateInstance().getContractID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(contractId))
                .get();
    }
}
