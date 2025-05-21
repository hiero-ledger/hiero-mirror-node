// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class FileDeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new FileDeleteTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setFileDelete(FileDeleteTransactionBody.newBuilder().setFileID(defaultEntityId.toFileID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.fileDelete().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var fileId = EntityId.of(recordItem.getTransactionBody().getFileDelete().getFileID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(fileId))
                .get();
        var expectedEntity = fileId.toEntity().toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(timestamp))
                .type(getExpectedEntityIdType())
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
