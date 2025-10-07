// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.junit.jupiter.api.Test;

final class NodeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeUpdateTransactionHandler(entityListener, entityIdService);
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
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var transaction = domainBuilder.transaction().get();
        var accountId = EntityId.of(nodeUpdate.getAccountId());
        when(entityIdService.lookup(nodeUpdate.getAccountId())).thenReturn(Optional.of(accountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        var adminKey = nodeUpdate.getAdminKey().toByteArray();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(nodeUpdate.getNodeId(), Node::getNodeId)
                .returns(accountId, Node::getAccountId)
                .returns(adminKey, Node::getAdminKey)
                .returns(null, Node::getCreatedTimestamp)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)
                .returns(new ServiceEndpoint("node1.hedera.com", "", 80), Node::getGrpcProxyEndpoint)));
    }

    @Test
    void nodeUpdateClearGrpcProxyEndpoint() {
        // given
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(b ->
                        b.setGrpcProxyEndpoint(com.hederahashgraph.api.proto.java.ServiceEndpoint.getDefaultInstance()))
                .build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1))
                .onNode(assertArg(
                        t -> assertThat(t).isNotNull().returns(ServiceEndpoint.CLEAR, Node::getGrpcProxyEndpoint)));
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
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var accountId = EntityId.of(nodeUpdate.getAccountId());
        when(entityIdService.lookup(nodeUpdate.getAccountId())).thenReturn(Optional.of(accountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var adminKey = nodeUpdate.getAdminKey().toByteArray();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(nodeUpdate.getNodeId(), Node::getNodeId)
                .returns(accountId, Node::getAccountId)
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
                .transactionBody(NodeUpdateTransactionBody.Builder::clearGrpcProxyEndpoint)
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
                .returns(null, Node::getAccountId)
                .returns(null, Node::getAdminKey)
                .returns(null, Node::getDeclineReward)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)
                .returns(null, Node::getGrpcProxyEndpoint)));
    }
}
