// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.junit.jupiter.api.Test;

class UncheckedSubmitTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UncheckedSubmitTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.uncheckedSubmit().build().getTransactionBody().toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.UNKNOWN;
    }

    @Override
    @Test
    void testGetEntityId() {
        var recordItem = recordItemBuilder.uncheckedSubmit().build();
        assertThat(transactionHandler.getEntity(recordItem)).isNull();
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.uncheckedSubmit().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
