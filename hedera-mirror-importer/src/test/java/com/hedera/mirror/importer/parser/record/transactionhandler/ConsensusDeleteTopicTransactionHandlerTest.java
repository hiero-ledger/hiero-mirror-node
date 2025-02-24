// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ConsensusDeleteTopicTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusDeleteTopicTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder()
                                .setTopicNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return TOPIC;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.consensusDeleteTopic().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var topicId = EntityId.of(
                recordItem.getTransactionBody().getConsensusDeleteTopic().getTopicID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(topicId))
                .get();
        var expectedEntity = topicId.toEntity().toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(timestamp))
                .type(getExpectedEntityIdType())
                .build();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
