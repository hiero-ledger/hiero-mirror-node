// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

final class RegisteredNodeCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new RegisteredNodeCreateTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.registeredNodeCreate().build().getTransactionBody().toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void testGetEntity() {
        assertThat(transactionHandler.getEntity(null)).isNull();
    }

    @Test
    void getType() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.REGISTEREDNODECREATE);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        final var recordItem = recordItemBuilder.registeredNodeCreate().build();
        final var nodeCreate = recordItem.getTransactionBody().getRegisteredNodeCreate();
        final var receipt = recordItem.getTransactionRecord().getReceipt();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(EntityId.EMPTY))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onRegisteredNode(assertArg(registeredNode -> {
            assertThat(registeredNode)
                    .isNotNull()
                    .returns(receipt.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                    .returns(consensusTimestamp, RegisteredNode::getCreatedTimestamp)
                    .returns(false, RegisteredNode::isDeleted);

            assertThat(registeredNode.getAdminKey())
                    .isEqualTo(nodeCreate.getAdminKey().toByteArray());
            assertThat(registeredNode.getDescription()).isEqualTo(nodeCreate.getDescription());
            assertThat(registeredNode.getServiceEndpoints()).hasSize(3).allMatch(e -> e.getPort() > 0);
        }));

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionUnsuccessful() {
        // given
        final var recordItem = recordItemBuilder
                .registeredNodeCreate()
                .status(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE)
                .build();
        final var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
