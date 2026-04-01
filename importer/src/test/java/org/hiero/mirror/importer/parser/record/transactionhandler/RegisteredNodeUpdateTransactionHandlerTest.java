// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.BLOCK_NODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.RegisteredNodeUpdateTransactionBody.Builder;
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

final class RegisteredNodeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new RegisteredNodeUpdateTransactionHandler(applicationEventPublisher, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.registeredNodeUpdate().build().getTransactionBody().toBuilder();
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
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.REGISTEREDNODEUPDATE);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        final var recordItem = recordItemBuilder.registeredNodeUpdate().build();
        final var nodeUpdate = recordItem.getTransactionBody().getRegisteredNodeUpdate();
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
                    .returns(nodeUpdate.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                    .returns(false, RegisteredNode::isDeleted);

            if (nodeUpdate.hasAdminKey()) {
                assertThat(registeredNode.getAdminKey())
                        .isEqualTo(nodeUpdate.getAdminKey().toByteArray());
            }
            if (nodeUpdate.hasDescription()
                    && !nodeUpdate.getDescription().getValue().isEmpty()) {
                assertThat(registeredNode.getDescription())
                        .isEqualTo(nodeUpdate.getDescription().getValue());
            }
            if (!nodeUpdate.getServiceEndpointList().isEmpty()) {
                assertThat(registeredNode.getServiceEndpoints())
                        .hasSizeGreaterThan(0)
                        .allMatch(e -> e.getPort() > 0);
                assertThat(registeredNode.getType()).containsExactly(BLOCK_NODE.getId());
            }
        }));
        verify(applicationEventPublisher, times(1)).publishEvent(any(RegisteredNodeChangedEvent.class));

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionUnsuccessful() {
        // given
        final var recordItem = recordItemBuilder
                .registeredNodeUpdate()
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
                .registeredNodeUpdate()
                .transactionBody(Builder::clearServiceEndpoint)
                .build();
        final var nodeUpdate = recordItem.getTransactionBody().getRegisteredNodeUpdate();
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
                    .returns(nodeUpdate.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                    .returns(false, RegisteredNode::isDeleted);
            assertThat(registeredNode.getServiceEndpoints()).isNull();
        }));
        verify(applicationEventPublisher, never()).publishEvent(any(RegisteredNodeChangedEvent.class));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
