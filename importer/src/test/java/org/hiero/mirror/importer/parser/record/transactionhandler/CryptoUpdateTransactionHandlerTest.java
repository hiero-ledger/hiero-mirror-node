// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

class CryptoUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EVMHookHandler evmHookHandler = mock(EVMHookHandler.class);

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoUpdateTransactionHandler(entityIdService, entityListener, evmHookHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoUpdateAccount(
                        CryptoUpdateTransactionBody.newBuilder().setAccountIDToUpdate(defaultEntityId.toAccountID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionDeclineReward(Boolean declineReward) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(body -> body.clear().setDeclineReward(BoolValue.of(declineReward)))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(declineReward, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 100})
    void updateTransactionStakedAccountId(long accountNum) {
        // Note, the sentinel value '0.0.0' clears the staked account id, in importer, we persist the encoded id '0' to
        // db to indicate there is no staked account id
        AccountID accountId = AccountID.newBuilder().setAccountNum(accountNum).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 28, 0)))
                .transactionBody(body ->
                        body.clear().setStakedAccountId(accountId).setMaxAutomaticTokenAssociations(Int32Value.of(-1)))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(null, Entity::getDeclineReward)
                .returns(-1, Entity::getMaxAutomaticTokenAssociations)
                .returns(accountNum, Entity::getStakedAccountId)
                .returns(-1L, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @CsvSource({"1,28", "-1,27"})
    void updateTransactionStakedNodeId(Long nodeId, int minorVersion) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, minorVersion, 0)))
                .transactionBody(body -> body.setStakedNodeId(nodeId))
                .build();
        setupForCryptoUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(0L, Entity::getStakedAccountId)
                .returns(true, Entity::getDeclineReward)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @Test
    void doNotUpdateTransactionStakedAccountIdBeforeConsensusStaking() {
        // Note, the HAPI version is less than 0.27.0 when staking was made live in services so staking will not happen
        AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .cryptoUpdate()
                .recordItem(r -> r.hapiVersion(new Version(0, 26, 0)))
                .transactionBody(body -> body.clear().setStakedAccountId(accountId))
                .build();
        var timestamp = withStakedNodeIdSet.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        getTransactionHandler().updateTransaction(transaction, withStakedNodeIdSet);
        assertCryptoUpdate(timestamp, t -> assertThat(t)
                .returns(null, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakePeriodStart));
    }

    private void assertCryptoUpdate(long timestamp, Consumer<Entity> extraAssert) {
        verify(entityListener, times(1))
                .onEntity(assertArg(t -> assertAll(
                        () -> assertThat(t)
                                .isNotNull()
                                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                                .returns(ACCOUNT, Entity::getType),
                        () -> extraAssert.accept(t))));
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getCryptoUpdateAccount();
        return getExpectedEntityTransactions(
                recordItem, transaction, EntityId.of(body.getStakedAccountId()), EntityId.of(body.getProxyAccountID()));
    }

    private void setupForCryptoUpdateTransactionTest(RecordItem recordItem, Consumer<Entity> extraAssertions) {
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        getTransactionHandler().updateTransaction(transaction, recordItem);
        assertCryptoUpdate(timestamp, extraAssertions);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void evmHookHandlerCalledWithHookCreationDetails() {
        // given
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.clearHookIdsToDelete())
                .build();
        var transaction = transaction(recordItem);
        var accountId = EntityId.of(
                recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(accountId.getId()),
                        eq(transactionBody.getHookCreationDetailsList()),
                        eq(List.of()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerCalledWithHookDeletionDetails() {
        // given
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.clearHookCreationDetails())
                .build();
        var transaction = transaction(recordItem);
        var accountId = EntityId.of(
                recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(accountId.getId()),
                        eq(transactionBody.getHookCreationDetailsList()),
                        eq(transactionBody.getHookIdsToDeleteList()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerCalledWithBothHookCreationAndDeletionDetails() {
        // given
        var recordItem = recordItemBuilder.cryptoUpdate().build();
        var transaction = transaction(recordItem);
        var accountId = EntityId.of(
                recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler)
                .process(
                        eq(recordItem),
                        eq(accountId.getId()),
                        eq(transactionBody.getHookCreationDetailsList()),
                        eq(transactionBody.getHookIdsToDeleteList()));

        // Verify entity was updated
        verify(entityListener).onEntity(any(Entity.class));
    }

    @Test
    void evmHookHandlerNotCalledWhenNoHooks() {
        // given
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.clearHookCreationDetails().clearHookIdsToDelete())
                .build();
        var transaction = transaction(recordItem);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(evmHookHandler).process(eq(recordItem), anyLong(), eq(List.of()), eq(List.of()));
    }

    private Transaction transaction(RecordItem recordItem) {
        var accountId = EntityId.of(
                recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(accountId))
                .get();
    }
}
