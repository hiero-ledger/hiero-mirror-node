// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;

import com.hederahashgraph.api.proto.java.ClprSubmitBundleTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.ClprContext;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Version;

class ClprSubmitBundleTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprSubmitBundleTransactionHandler();
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
    void updateTransactionClprChannelPresent() {
        // given
        final var transactionBody = getDefaultTransactionBody().build();
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(transactionBody.getClprSubmitBundle().getChannelId())
                .build();
        final var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprChannelValue);
        final var transaction = domainBuilder.transaction().get();

        // when/then -- actual persistence is dispatched generically from EntityRecordItemListener, so this handler
        // has nothing left to do but validate the expected state change is present
        assertThatCode(() -> transactionHandler.updateTransaction(transaction, recordItem))
                .doesNotThrowAnyException();
    }

    @Test
    void updateTransactionMissingClprChannel() {
        // given
        final var recordItem = buildRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build(),
                null);
        final var transaction = domainBuilder.transaction().get();

        // when/then -- logs a recoverable error internally but must not throw
        assertThatCode(() -> transactionHandler.updateTransaction(transaction, recordItem))
                .doesNotThrowAnyException();
    }

    private RecordItem buildRecordItem(
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.hederahashgraph.api.proto.java.ClprChannel clprChannel) {
        final var transaction = Transaction.newBuilder()
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
                .clprContext(ClprContext.builder()
                        .clprChannels(
                                clprChannel == null
                                        ? List.of()
                                        : List.of(new ClprChannelEntry(
                                                clprChannel.getChannelId(), clprChannelMapUpdate(clprChannel))))
                        .build())
                .build();
    }
}
