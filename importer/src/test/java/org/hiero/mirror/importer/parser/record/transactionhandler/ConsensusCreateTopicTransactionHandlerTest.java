// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.hiero.mirror.importer.TestUtils.toEntityTransactions;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.junit.jupiter.api.Test;

class ConsensusCreateTopicTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusCreateTopicTransactionHandler(
                entityIdService,
                entityListener,
                new ConsensusUpdateTopicTransactionHandler(entityIdService, entityListener));
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setTopicID(defaultEntityId.toTopicID());
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
                .consensusCreateTopic()
                .transactionBody(b -> b.clearCustomFees()
                        .addCustomFees(FixedCustomFee.newBuilder()
                                .setFixedFee(
                                        FixedFee.newBuilder().setAmount(100L).setDenominatingTokenId(feeTokenId))
                                .setFeeCollectorAccountId(feeCollector))
                        .addCustomFees(FixedCustomFee.newBuilder()
                                .setFixedFee(FixedFee.newBuilder().setAmount(200L))
                                .setFeeCollectorAccountId(feeCollector)))
                .build();
        var body = recordItem.getTransactionBody().getConsensusCreateTopic();
        var autoRenewAccountId = EntityId.of(body.getAutoRenewAccount());
        long timestamp = recordItem.getConsensusTimestamp();
        var topicId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTopicID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntityTransactions = toEntityTransactions(
                recordItem,
                autoRenewAccountId,
                transaction.getEntityId(),
                transaction.getNodeAccountId(),
                transaction.getPayerAccountId(),
                EntityId.of(feeCollector),
                EntityId.of(feeTokenId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedCustomFee = CustomFee.builder()
                .entityId(topicId.getId())
                .fixedFees(List.of(
                        org.hiero.mirror.common.domain.token.FixedFee.builder()
                                .amount(100L)
                                .collectorAccountId(EntityId.of(feeCollector))
                                .denominatingTokenId(EntityId.of(feeTokenId))
                                .build(),
                        org.hiero.mirror.common.domain.token.FixedFee.builder()
                                .amount(200L)
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
                FeeExemptKeyList.newBuilder()
                        .addAllKeys(body.getFeeExemptKeyListList())
                        .build()
                        .toByteArray(),
                body.getFeeScheduleKey().toByteArray(),
                body.getSubmitKey().toByteArray());
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulNoAutoRenewAccountIdNoKeysNoCustomFees() {
        // given
        var recordItem = recordItemBuilder
                .consensusCreateTopic()
                .transactionBody(b -> b.clearAdminKey()
                        .clearAutoRenewAccount()
                        .clearCustomFees()
                        .clearFeeExemptKeyList()
                        .clearFeeScheduleKey()
                        .clearSubmitKey())
                .build();
        var topicId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTopicID());
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedCustomFee = CustomFee.builder()
                .entityId(topicId.getId())
                .fixedFees(Collections.emptyList())
                .timestampRange(Range.atLeast(timestamp))
                .build();
        verify(entityListener).onCustomFee(assertArg(c -> assertThat(c).isEqualTo(expectedCustomFee)));
        assertEntity(timestamp, topicId, null);
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
        verify(entityListener).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
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
                .returns(timestamp, Topic::getCreatedTimestamp)
                .returns(expectedFeeExemptKeyList, Topic::getFeeExemptKeyList)
                .returns(expectedFeeScheduleKey, Topic::getFeeScheduleKey)
                .returns(id, Topic::getId)
                .returns(expectedSubmitKey, Topic::getSubmitKey)
                .returns(Range.atLeast(timestamp), Topic::getTimestampRange)));
    }
}
