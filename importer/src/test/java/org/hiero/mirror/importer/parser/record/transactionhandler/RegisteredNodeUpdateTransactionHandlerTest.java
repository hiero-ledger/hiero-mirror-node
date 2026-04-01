// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.BLOCK_NODE;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.GENERAL_SERVICE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.RegisteredNodeUpdateTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
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
        verify(entityListener, times(1)).onRegisteredNode(assertArg(registeredNode -> assertThat(registeredNode)
                .returns(nodeUpdate.getAdminKey().toByteArray(), RegisteredNode::getAdminKey)
                .returns(false, RegisteredNode::isDeleted)
                .returns(nodeUpdate.getDescription().getValue(), RegisteredNode::getDescription)
                .returns(nodeUpdate.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                .returns(Range.atLeast(consensusTimestamp), RegisteredNode::getTimestampRange)
                .returns(List.of(BLOCK_NODE.getId(), GENERAL_SERVICE.getId()), RegisteredNode::getType)
                .extracting(
                        RegisteredNode::getServiceEndpoints,
                        InstanceOfAssertFactories.list(RegisteredServiceEndpoint.class))
                .hasSize(2)
                .allMatch(e -> e.getPort() > 0)));
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
        verify(entityListener, times(1)).onRegisteredNode(assertArg(registeredNode -> assertThat(registeredNode)
                .returns(nodeUpdate.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                .returns(false, RegisteredNode::isDeleted)
                .returns(null, RegisteredNode::getServiceEndpoints)
                .returns(Range.atLeast(consensusTimestamp), RegisteredNode::getTimestampRange)
                .returns(null, RegisteredNode::getType)));
        verify(applicationEventPublisher, never()).publishEvent(any(RegisteredNodeChangedEvent.class));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
