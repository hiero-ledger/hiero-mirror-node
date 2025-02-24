// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.importer.TestUtils.toEntityTransactions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class ConsensusSubmitMessageTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ConsensusSubmitMessageTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder()
                                .setTopicNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOPIC;
    }

    @Override
    protected boolean isSkipMainEntityTransaction() {
        return true;
    }

    @ParameterizedTest
    @CsvSource({"0,1", "2,2", "3,"})
    void updateTransaction(long runningHashVersion, Integer expectedRunningHashVersion) {
        // Given
        var recordItem = recordItemBuilder
                .consensusSubmitMessage()
                .receipt(r -> r.setTopicRunningHashVersion(runningHashVersion))
                .build();
        var transaction = domainBuilder.transaction().get();
        var topicMessage = ArgumentCaptor.forClass(TopicMessage.class);
        var transactionBody = recordItem.getTransactionBody().getConsensusSubmitMessage();
        var receipt = recordItem.getTransactionRecord().getReceipt();
        var expectedEntityTransactions =
                toEntityTransactions(recordItem, transaction.getNodeAccountId(), transaction.getPayerAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onTopicMessage(topicMessage.capture());
        assertThat(topicMessage.getValue())
                .returns(transactionBody.getChunkInfo().getNumber(), TopicMessage::getChunkNum)
                .returns(transactionBody.getChunkInfo().getTotal(), TopicMessage::getChunkTotal)
                .returns(transaction.getConsensusTimestamp(), TopicMessage::getConsensusTimestamp)
                .returns(
                        transactionBody.getChunkInfo().getInitialTransactionID().toByteArray(),
                        TopicMessage::getInitialTransactionId)
                .returns(transactionBody.getMessage().toByteArray(), TopicMessage::getMessage)
                .returns(recordItem.getPayerAccountId(), TopicMessage::getPayerAccountId)
                .returns(receipt.getTopicRunningHash().toByteArray(), TopicMessage::getRunningHash)
                .returns(expectedRunningHashVersion, TopicMessage::getRunningHashVersion)
                .returns(receipt.getTopicSequenceNumber(), TopicMessage::getSequenceNumber)
                .returns(transaction.getEntityId(), TopicMessage::getTopicId);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTopics(false);
        var recordItem = recordItemBuilder.consensusSubmitMessage().build();
        var transaction = domainBuilder.transaction().get();
        var expectedEntityTransactions =
                toEntityTransactions(recordItem, transaction.getNodeAccountId(), transaction.getPayerAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
