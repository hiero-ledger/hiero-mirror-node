// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.Node;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class NodeCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeCreateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeCreate(NodeCreateTransactionBody.newBuilder()
                        .setAccountId(AccountID.newBuilder()
                                .setAccountNum(DEFAULT_ENTITY_NUM)
                                .build())
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

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = recordItem.getTransaction().toByteArray();
        var transactionRecordBytes = recordItem.getTransactionRecord().toByteArray();

        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);

        var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
        verify(entityListener, times(1)).onNode(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), Node::getCreatedTimestamp)
                .returns(recordItem.getTransactionRecord().getReceipt().getNodeId(), Node::getNodeId)
                .returns(nodeCreate.getAdminKey().toByteArray(), Node::getAdminKey)
                .returns(nodeCreate.getDeclineReward(), Node::getDeclineReward)
                .returns(recordItem.getConsensusTimestamp(), Node::getTimestampLower)
                .returns(false, Node::isDeleted)));
    }
}
