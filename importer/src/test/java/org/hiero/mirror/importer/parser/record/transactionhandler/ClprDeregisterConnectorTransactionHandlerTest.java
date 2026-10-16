// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprDeregisterConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClprDeregisterConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprDeregisterConnectorTransactionHandler(entityListener);
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
        var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        var body = recordItem.getTransactionBody().getClprDeregisterConnector();
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprConnector.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprConnector(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(true, ClprConnector::getDeleted)
                .returns(EntityId.of(body.getStakeRecipient()), ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }
}
