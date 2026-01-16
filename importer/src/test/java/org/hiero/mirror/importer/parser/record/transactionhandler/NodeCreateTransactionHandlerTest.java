// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.junit.jupiter.api.Test;

class NodeCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeCreateTransactionHandler(entityListener, entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeCreate(NodeCreateTransactionBody.newBuilder()
                        .setAccountId(defaultEntityId.toAccountID())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void nodeCreate() {
        // given
        var recordItem = recordItemBuilder.nodeCreate().build();
        var transaction = domainBuilder.transaction().get();
        var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
        var endpoint = nodeCreate.getGrpcProxyEndpoint();
        var accountId = EntityId.of(nodeCreate.getAccountId());
        when(entityIdService.lookup(nodeCreate.getAccountId())).thenReturn(Optional.of(accountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(accountId, Node::getAccountId)
                .returns(recordItem.getConsensusTimestamp(), Node::getCreatedTimestamp)
                .returns(recordItem.getTransactionRecord().getReceipt().getNodeId(), Node::getNodeId)
                .returns(nodeCreate.getAdminKey().toByteArray(), Node::getAdminKey)
                .returns(nodeCreate.getDeclineReward(), Node::getDeclineReward)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(
                        new ServiceEndpoint(endpoint.getDomainName(), "", endpoint.getPort()),
                        Node::getGrpcProxyEndpoint)
                .returns(false, Node::isDeleted)));
    }

    @Test
    void nodeCreateIpAddress() {
        // given
        var ip = ByteString.copyFrom(new byte[] {1, 2, 3, 4});
        var recordItem = recordItemBuilder
                .nodeCreate()
                .transactionBody(
                        b -> b.getGrpcProxyEndpointBuilder().clearDomainName().setIpAddressV4(ip))
                .build();
        var transaction = domainBuilder.transaction().get();
        var endpoint = recordItem.getTransactionBody().getNodeCreate().getGrpcProxyEndpoint();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(new ServiceEndpoint("", "1.2.3.4", endpoint.getPort()), Node::getGrpcProxyEndpoint)));
    }

    @Test
    void nodeCreateInvalidIpAddress() {
        // given
        var ip = ByteString.copyFrom(new byte[] {1, 2, 3, 4, 6});
        var recordItem = recordItemBuilder
                .nodeCreate()
                .transactionBody(
                        b -> b.getGrpcProxyEndpointBuilder().clearDomainName().setIpAddressV4(ip))
                .build();
        var transaction = domainBuilder.transaction().get();
        var endpoint = recordItem.getTransactionBody().getNodeCreate().getGrpcProxyEndpoint();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(new ServiceEndpoint("", "", endpoint.getPort()), Node::getGrpcProxyEndpoint)));
    }

    @Test
    void nodeCreateMinimal() {
        // given
        var recordItem = recordItemBuilder
                .nodeCreate()
                .transactionBody(b -> b.clearAdminKey().clearGrpcProxyEndpoint())
                .build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(null, Node::getAdminKey)
                .returns(null, Node::getGrpcProxyEndpoint)));
    }
}
