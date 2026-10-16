// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprCloseChannelTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprCloseChannelTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprCloseChannelTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprCloseChannel(ClprCloseChannelTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setOwnershipCommitment(DomainUtils.fromBytes(domainBuilder.bytes(32))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionChannelExists() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var body = transactionBody.getClprCloseChannel();
        var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(body.getChannelId())
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSING)
                .setNextMessageId(5)
                .setAckedMessageId(4)
                .setSentRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setReceivedMessageId(3)
                .setReceivedRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .build();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprChannelValue);
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprChannel.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprChannel(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprChannel::getChannelId)
                .returns(ClprChannelStatus.CLOSING, ClprChannel::getStatus)
                .returns(5L, ClprChannel::getNextMessageId)
                .returns(4L, ClprChannel::getAckedMessageId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprChannel::getTimestampRange);
        verifyNoMoreInteractions(entityListener);
    }

    @Test
    void updateTransactionAbandonedCommitment() {
        // given
        var recordItem = buildRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                null);
        var body = recordItem.getTransactionBody().getClprCloseChannel();
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprChannelPendingCommitment.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprChannelPendingCommitment(captor.capture());
        assertThat(captor.getValue())
                .returns(
                        DomainUtils.toBytes(body.getOwnershipCommitment()),
                        ClprChannelPendingCommitment::getOwnershipCommitment)
                .returns(true, ClprChannelPendingCommitment::getDeleted)
                .returns(
                        Range.atLeast(recordItem.getConsensusTimestamp()),
                        ClprChannelPendingCommitment::getTimestampRange);
        verifyNoMoreInteractions(entityListener);
    }

    private RecordItem buildRecordItem(
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.hederahashgraph.api.proto.java.ClprChannel clprChannel) {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setSigMap(getDefaultSigMap())
                        .build()
                        .toByteString())
                .build();
        return RecordItem.builder()
                .entityTransactionPredicate(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .hapiVersion(new Version(0, 28, 0))
                .transaction(transaction)
                .transactionRecord(transactionRecord)
                .clprChannel(clprChannel)
                .build();
    }
}
