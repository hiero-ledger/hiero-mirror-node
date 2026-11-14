// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprRegisterChannelTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        // given -- ownership_commitment is on the transaction body, so no state change is needed
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var body = recordItem.getTransactionBody().getClprRegisterChannel();
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprChannelPendingCommitment.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprChannelPendingCommitment(captor.capture());
        assertThat(captor.getValue())
                .returns(
                        DomainUtils.toBytes(body.getOwnershipCommitment()),
                        ClprChannelPendingCommitment::getOwnershipCommitment)
                .returns(consensusTimestamp, ClprChannelPendingCommitment::getCreatedTimestamp)
                .returns(ClprPendingCommitmentStatus.OPEN, ClprChannelPendingCommitment::getStatus)
                .returns(Range.atLeast(consensusTimestamp), ClprChannelPendingCommitment::getTimestampRange);
    }
}
