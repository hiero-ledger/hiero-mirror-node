// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.ClprSubmitBundleTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.util.Version;

class ClprSubmitBundleTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private ClprStateChangeHandler clprStateChangeHandler;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprSubmitBundleTransactionHandler(clprStateChangeHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprSubmitBundle(ClprSubmitBundleTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setBundlePayload(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setEndpointNodeId(3));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionDelegatesToClprStateChangeHandler() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(transactionBody.getClprSubmitBundle().getChannelId())
                .build();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprChannelValue);
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(clprStateChangeHandler).process(recordItem);
    }

    @Test
    void updateTransactionMissingClprChannel() {
        // given
        var recordItem = buildRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                null);
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(clprStateChangeHandler);
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
