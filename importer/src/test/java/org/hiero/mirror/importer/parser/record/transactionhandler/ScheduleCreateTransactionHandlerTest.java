// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ScheduleCreateTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        SchedulableTransactionBody.Builder scheduledTransactionBodyBuilder = SchedulableTransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
        return TransactionBody.newBuilder()
                .setScheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(DEFAULT_KEY)
                        .setPayerAccountID(AccountID.newBuilder()
                                .setShardNum(0)
                                .setRealmNum(0)
                                .setAccountNum(1)
                                .build())
                        .setMemo("schedule memo")
                        .setScheduledTransactionBody(scheduledTransactionBodyBuilder)
                        .build());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setScheduleID(defaultEntityId.toScheduleID());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.SCHEDULE;
    }

    @Test
    void updateTransactionSchedulesDisabled() {
        // given
        entityProperties.getPersist().setSchedules(false);
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, never()).onSchedule(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        var schedulePayerAccountId =
                EntityId.of(recordItem.getTransactionBody().getScheduleCreate().getPayerAccountID());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .satisfies(s -> assertThat(s.getExpirationTime()).isPositive())
                .returns(schedulePayerAccountId, Schedule::getPayerAccountId)
                .satisfies(s -> assertThat(s.getScheduleId()).isNotNull())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(true, Schedule::isWaitForExpiry)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, schedulePayerAccountId));
    }

    @Test
    void updateTransactionSuccessfulNoExpirationTime() {
        // given
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .transactionBody(
                        b -> b.clearExpirationTime().clearPayerAccountID().setWaitForExpiry(false))
                .receipt(r -> r.setScheduleID(recordItemBuilder.scheduleId()))
                .build();
        var payerAccountId = recordItem.getPayerAccountId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(timestamp, Entity::getTimestampLower)));
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .returns(null, Schedule::getExpirationTime)
                .returns(payerAccountId, Schedule::getPayerAccountId)
                .satisfies(s -> assertThat(s.getScheduleId()).isNotNull())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(false, Schedule::isWaitForExpiry)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, payerAccountId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void handleScheduleCreateWithParentEthereumTransaction(boolean isCreate) {
        // given
        var senderId = recordItemBuilder.accountId();
        var senderIdAsEntityId = EntityId.of(senderId);
        var scheduleId = recordItemBuilder.scheduleId();
        var ethereumTransaction = domainBuilder.ethereumTransaction(isCreate).get();

        // Build parent RecordItem with EthereumTransaction
        var parentRecordItem = recordItemBuilder
                .ethereumTransaction(isCreate)
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .record(r -> r.setContractCreateResult(recordItemBuilder
                        .contractFunctionResult()
                        .setSenderId(senderId)
                        .build()))
                .build();

        // Build child RecordItem (ScheduleCreate) with parent reference
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .recordItem(r -> r.parent(parentRecordItem).previous(parentRecordItem))
                .receipt(r -> r.setScheduleID(scheduleId))
                .record(r -> r.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .build();

        var payerId = recordItem.getPayerAccountId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = getTransaction(payerId, timestamp);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(senderIdAsEntityId, Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .satisfies(s -> assertThat(s.getExpirationTime()).isPositive())
                .satisfies(s -> assertThat(s.getScheduleId()).isNotNull())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(true, Schedule::isWaitForExpiry)));
    }

    @Test
    void updateTransactionEthereumWithEmptyFunctionResult() {
        // given
        var scheduleId = recordItemBuilder.scheduleId();
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();

        // Build parent RecordItem with EthereumTransaction
        var parentRecordItem = recordItemBuilder
                .ethereumTransaction(false)
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .record(r -> r.setContractCreateResult(
                                ContractFunctionResult.newBuilder().build())
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().build()))
                .build();

        // Build child RecordItem (ScheduleCreate) with parent reference
        var recordItem = recordItemBuilder
                .scheduleCreate()
                .recordItem(r -> r.parent(parentRecordItem).previous(parentRecordItem))
                .receipt(r -> r.setScheduleID(scheduleId))
                .record(r -> r.setParentConsensusTimestamp(
                        parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .build();

        var payerId = recordItem.getPayerAccountId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = getTransaction(payerId, timestamp);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onSchedule(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(timestamp, Schedule::getConsensusTimestamp)
                .returns(payerId, Schedule::getCreatorAccountId)
                .returns(null, Schedule::getExecutedTimestamp)
                .satisfies(s -> assertThat(s.getExpirationTime()).isPositive())
                .satisfies(s -> assertThat(s.getScheduleId()).isNotNull())
                .satisfies(s -> assertThat(s.getTransactionBody()).isNotEmpty())
                .returns(true, Schedule::isWaitForExpiry)));
    }

    private Transaction getTransaction(final EntityId payerId, final Long timestamp) {
        return Transaction.builder()
                .chargedTxFee(10000000L)
                .consensusTimestamp(timestamp)
                .nonce(0)
                .payerAccountId(payerId)
                .result(ResponseCodeEnum.SUCCESS.getNumber())
                .scheduled(false)
                .type(TransactionType.SCHEDULECREATE.getProtoId())
                .validDurationSeconds(120L)
                .build();
    }
}
