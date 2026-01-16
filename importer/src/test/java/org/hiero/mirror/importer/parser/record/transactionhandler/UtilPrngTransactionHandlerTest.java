// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Prng;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

class UtilPrngTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<Prng> pseudoRandomGenerates;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UtilPrngTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder.prng(0).build().getTransactionBody().toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionRandomNumber() {
        // given
        int range = 8;
        var recordItem = recordItemBuilder.prng(range).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();
        int randomNumber = recordItem.getTransactionRecord().getPrngNumber();
        var expected = Prng.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .payerAccountId(recordItem.getPayerAccountId().getId())
                .prngNumber(randomNumber)
                .range(range)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onPrng(pseudoRandomGenerates.capture());
        assertThat(pseudoRandomGenerates.getAllValues()).containsOnly(expected);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionRandomBytes() {
        // given
        int range = 0;
        var recordItem = recordItemBuilder.prng(range).build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();
        byte[] randomBytes = recordItem.getTransactionRecord().getPrngBytes().toByteArray();
        var expected = Prng.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .payerAccountId(recordItem.getPayerAccountId().getId())
                .prngBytes(randomBytes)
                .range(range)
                .build();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onPrng(pseudoRandomGenerates.capture());
        assertThat(pseudoRandomGenerates.getAllValues()).containsOnly(expected);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionFailedTransaction() {
        // given
        var recordItem = recordItemBuilder
                .prng(1)
                .status(ResponseCodeEnum.DUPLICATE_TRANSACTION)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionEntropyNotSet() {
        // given
        var recordItem = recordItemBuilder
                .prng(1)
                .record(TransactionRecord.Builder::clearEntropy)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
