// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.FixedCustomFeeList;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ConsensusUpdateTopicTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusUpdateTopicTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusUpdateTopic(
                        ConsensusUpdateTopicTransactionBody.newBuilder().setTopicID(defaultEntityId.toTopicID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOPIC;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var feeCollector = recordItemBuilder.accountId();
        var feeTokenId = recordItemBuilder.tokenId();
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clearCustomFees()
                        .setCustomFees(FixedCustomFeeList.newBuilder()
                                .addFees(FixedCustomFee.newBuilder()
                                        .setFixedFee(FixedFee.newBuilder()
                                                .setAmount(200L)
                                                .setDenominatingTokenId(feeTokenId))
                                        .setFeeCollectorAccountId(feeCollector))
                                .addFees(FixedCustomFee.newBuilder()
                                        .setFixedFee(FixedFee.newBuilder().setAmount(300L))
                                        .setFeeCollectorAccountId(feeCollector))))
                .build();
        var body = recordItem.getTransactionBody().getConsensusUpdateTopic();
        var topicId = EntityId.of(body.getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var autoRenewAccountId = EntityId.of(10L);
        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem, transaction, autoRenewAccountId, EntityId.of(feeCollector), EntityId.of(feeTokenId));
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(autoRenewAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedCustomFee = CustomFee.builder()
                .entityId(topicId.getId())
                .fixedFees(List.of(
                        org.hiero.mirror.common.domain.token.FixedFee.builder()
                                .amount(200L)
                                .collectorAccountId(EntityId.of(feeCollector))
                                .denominatingTokenId(EntityId.of(feeTokenId))
                                .build(),
                        org.hiero.mirror.common.domain.token.FixedFee.builder()
                                .amount(300L)
                                .collectorAccountId(EntityId.of(feeCollector))
                                .build()))
                .timestampRange(Range.atLeast(timestamp))
                .build();
        verify(entityListener).onCustomFee(assertArg(c -> assertThat(c).isEqualTo(expectedCustomFee)));
        assertEntity(timestamp, topicId, autoRenewAccountId.getId());
        assertTopic(
                topicId.getId(),
                timestamp,
                body.getAdminKey().toByteArray(),
                body.getFeeExemptKeyList().toByteArray(),
                body.getFeeScheduleKey().toByteArray(),
                body.getSubmitKey().toByteArray());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clearAdminKey()
                        .clearCustomFees()
                        .clearFeeExemptKeyList()
                        .clearFeeScheduleKey()
                        .clearSubmitKey()
                        .setAutoRenewAccount(aliasAccountId))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var autoRenewAccountId = EntityId.of(10L);
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction, autoRenewAccountId);
        when(entityIdService.lookup(aliasAccountId)).thenReturn(Optional.of(autoRenewAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, never()).onCustomFee(any());
        assertEntity(timestamp, topicId, autoRenewAccountId.getId());
        assertTopic(topicId.getId(), timestamp, null, null, null, null);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionEntityIdEmpty(EntityId entityId) {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clearAdminKey()
                        .clearCustomFees()
                        .clearFeeExemptKeyList()
                        .clearFeeScheduleKey()
                        .clearSubmitKey()
                        .setAutoRenewAccount(aliasAccountId))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        when(entityIdService.lookup(aliasAccountId)).thenReturn(Optional.ofNullable(entityId));
        var expectedId = entityId == null ? null : entityId.getId();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, never()).onCustomFee(any());
        assertEntity(timestamp, topicId, expectedId);
        assertTopic(topicId.getId(), timestamp, null, null, null, null);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountIdAndFeeExemptKeyList() {
        // given
        var recordItem = recordItemBuilder
                .consensusUpdateTopic()
                .transactionBody(b -> b.clearAdminKey()
                        .clearAutoRenewAccount()
                        .clearCustomFees()
                        .clearFeeScheduleKey()
                        .clearSubmitKey()
                        .setAutoRenewAccount(AccountID.newBuilder().setAccountNum(0))
                        .setFeeExemptKeyList(FeeExemptKeyList.newBuilder()))
                .build();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusUpdateTopic().getTopicID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, never()).onCustomFee(any());
        assertEntity(timestamp, topicId, 0L);
        assertTopic(
                topicId.getId(),
                timestamp,
                null,
                FeeExemptKeyList.newBuilder().build().toByteArray(),
                null,
                null);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    private void assertEntity(long timestamp, EntityId topicId, Long expectedAutoRenewAccountId) {
        verify(entityListener, times(1)).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(topicId.getId(), Entity::getId)
                .returns(topicId.getNum(), Entity::getNum)
                .returns(topicId.getRealm(), Entity::getRealm)
                .returns(topicId.getShard(), Entity::getShard)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(TOPIC, Entity::getType)));
    }

    private void assertTopic(
            long id,
            long timestamp,
            byte[] expectedAdminKey,
            byte[] expectedFeeExemptKeyList,
            byte[] expectedFeeScheduleKey,
            byte[] expectedSubmitKey) {
        verify(entityListener).onTopic(assertArg(t -> assertThat(t)
                .returns(expectedAdminKey, Topic::getAdminKey)
                .returns(null, Topic::getCreatedTimestamp)
                .returns(expectedFeeExemptKeyList, Topic::getFeeExemptKeyList)
                .returns(expectedFeeScheduleKey, Topic::getFeeScheduleKey)
                .returns(id, Topic::getId)
                .returns(expectedSubmitKey, Topic::getSubmitKey)
                .returns(Range.atLeast(timestamp), Topic::getTimestampRange)));
    }
}
