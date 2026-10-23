// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprRegisterConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprRegisterConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprRegisterConnectorTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprRegisterConnector(ClprRegisterConnectorTransactionBody.newBuilder()
                        .setCommitment(DomainUtils.fromBytes(domainBuilder.bytes(32))));
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
        var commitment = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), commitment);
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprConnectorPendingCommitment.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprConnectorPendingCommitment(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(commitment), ClprConnectorPendingCommitment::getCommitment)
                .returns(consensusTimestamp, ClprConnectorPendingCommitment::getCreatedTimestamp)
                .returns(false, ClprConnectorPendingCommitment::getDeleted)
                .returns(Range.atLeast(consensusTimestamp), ClprConnectorPendingCommitment::getTimestampRange);
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
            TransactionBody body, TransactionRecord transactionRecord, com.google.protobuf.ByteString commitment) {
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
                .clprConnectorPendingCommitment(commitment)
                .build();
    }
}
