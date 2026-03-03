// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

final class RegisteredNodeDeleteTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new RegisteredNodeDeleteTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.registeredNodeDelete().build().getTransactionBody().toBuilder();
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
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.REGISTEREDNODEDELETE);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        final var recordItem = recordItemBuilder.registeredNodeDelete().build();
        final var deleteBody = recordItem.getTransactionBody().getRegisteredNodeDelete();
        final var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, times(1)).onRegisteredNode(assertArg(registeredNode -> {
            assertThat(registeredNode)
                    .isNotNull()
                    .returns(deleteBody.getRegisteredNodeId(), RegisteredNode::getRegisteredNodeId)
                    .returns(true, RegisteredNode::isDeleted);
        }));

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionUnsuccessful() {
        // given
        final var recordItem = recordItemBuilder
                .registeredNodeDelete()
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
