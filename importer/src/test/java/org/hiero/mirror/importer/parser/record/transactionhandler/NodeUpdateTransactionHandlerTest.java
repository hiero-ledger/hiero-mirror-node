// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.Node;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

class NodeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeUpdateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeUpdate(NodeUpdateTransactionBody.newBuilder()
                        .setAccountId(defaultEntityId.toAccountID())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void nodeUpdate() {
        // given
        var recordItem = recordItemBuilder.nodeUpdate().build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        var adminKey =
                recordItem.getTransactionBody().getNodeUpdate().getAdminKey().toByteArray();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(recordItem.getTransactionBody().getNodeUpdate().getNodeId(), Node::getNodeId)
                .returns(adminKey, Node::getAdminKey)
                .returns(null, Node::getCreatedTimestamp)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)));
    }

    @Test
    void nodeUpdateMigration() {
        // given
        var transactionId = TransactionID.newBuilder().setNonce(1);
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .record(b -> b.setTransactionID(transactionId))
                .build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var adminKey = nodeUpdate.getAdminKey().toByteArray();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(recordItem.getTransactionBody().getNodeUpdate().getNodeId(), Node::getNodeId)
                .returns(adminKey, Node::getAdminKey)
                .returns(nodeUpdate.getDeclineReward().getValue(), Node::getDeclineReward)
                .returns(recordItem.getConsensusTimestamp(), Node::getCreatedTimestamp)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)));
    }

    @Test
    void nodeUpdateMissingFields() {
        // given
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(NodeUpdateTransactionBody.Builder::clearAdminKey)
                .transactionBody(NodeUpdateTransactionBody.Builder::clearDeclineReward)
                .build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(recordItem.getTransactionBody().getNodeUpdate().getNodeId(), Node::getNodeId)
                .returns(null, Node::getAdminKey)
                .returns(null, Node::getDeclineReward)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)));
    }
}
