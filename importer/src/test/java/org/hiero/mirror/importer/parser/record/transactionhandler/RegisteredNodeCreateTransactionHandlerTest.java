// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.BLOCK_NODE;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.MIRROR_NODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.RegisteredNodeCreateTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.RegisteredNodeChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

final class RegisteredNodeCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new RegisteredNodeCreateTransactionHandler(applicationEventPublisher, entityListener);
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
            assertThat(registeredNode.getType()).containsExactlyInAnyOrder(BLOCK_NODE.getId(), MIRROR_NODE.getId());
        }));
        verify(applicationEventPublisher, times(1)).publishEvent(any(RegisteredNodeChangedEvent.class));

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
        verifyNoInteractions(applicationEventPublisher);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionWithNoServiceEndpointsDoesNotPublishEvent() {
        // given
        final var recordItem = recordItemBuilder
                .registeredNodeCreate()
                .transactionBody(Builder::clearServiceEndpoint)
                .build();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(EntityId.EMPTY))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onRegisteredNode(any());
        verifyNoInteractions(applicationEventPublisher);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
