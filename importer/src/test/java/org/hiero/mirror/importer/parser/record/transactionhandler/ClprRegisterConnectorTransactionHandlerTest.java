// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprRegisterConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        var body = recordItem.getTransactionBody().getClprRegisterConnector();
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprConnectorPendingCommitment.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprConnectorPendingCommitment(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getCommitment()), ClprConnectorPendingCommitment::getCommitment)
                .returns(consensusTimestamp, ClprConnectorPendingCommitment::getCreatedTimestamp)
                .returns(false, ClprConnectorPendingCommitment::getDeleted)
                .returns(Range.atLeast(consensusTimestamp), ClprConnectorPendingCommitment::getTimestampRange);
    }
}
