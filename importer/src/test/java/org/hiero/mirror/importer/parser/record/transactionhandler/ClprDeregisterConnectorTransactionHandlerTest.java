// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprDeregisterConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class ClprDeregisterConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private ClprConnectorCommitmentService clprConnectorCommitmentService;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprDeregisterConnectorTransactionHandler(clprConnectorCommitmentService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprDeregisterConnector(ClprDeregisterConnectorTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setConnectorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setStakeRecipient(domainBuilder.entityNum(1000L).toAccountID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var body = recordItem.getTransactionBody().getClprDeregisterConnector();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorCommitmentService.lookup(
                        DomainUtils.toBytes(body.getChannelId()), DomainUtils.toBytes(body.getConnectorId())))
                .thenReturn(commitment);
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprConnector.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprConnector(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(commitment, ClprConnector::getCommitment)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(EntityId.of(body.getStakeRecipient()), ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void updateTransactionMissingCommitment() {
        // given -- the connector completion that would have warmed the commitment lookup is missing/not yet seen
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
    }
}
