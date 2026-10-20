// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprRegisterChannelTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprRegisterChannelTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprRegisterChannelTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprRegisterChannel(ClprRegisterChannelTransactionBody.newBuilder()
                        .setOwnershipCommitment(DomainUtils.fromBytes(domainBuilder.bytes(32))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var transactionBody = getDefaultTransactionBody().build();

        // Deliberately differs from the tx body to prove the commitment is read off the state change.
        var ownershipCommitment = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), ownershipCommitment);
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprChannelPendingCommitment.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprChannelPendingCommitment(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(ownershipCommitment), ClprChannelPendingCommitment::getOwnershipCommitment)
                .returns(consensusTimestamp, ClprChannelPendingCommitment::getCreatedTimestamp)
                .returns(false, ClprChannelPendingCommitment::getDeleted)
                .returns(Range.atLeast(consensusTimestamp), ClprChannelPendingCommitment::getTimestampRange);
    }

    @Test
    void updateTransactionMissingStateChange() {
        // given
        var recordItem = buildRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                null);
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    private RecordItem buildRecordItem(
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.google.protobuf.ByteString ownershipCommitment) {
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
                .clprChannelPendingCommitment(ownershipCommitment)
                .build();
    }
}
