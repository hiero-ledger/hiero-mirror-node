// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprRegisterConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClprRegisterConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprRegisterConnectorTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprRegisterConnector(ClprRegisterConnectorTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setConnectorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setIdentityKey(recordItemBuilder.key())
                        .setSalt(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setConnectorContract(contractId)
                        .setAdminKey(recordItemBuilder.key())
                        .setLockedStake(100_000_000L));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void updateTransaction() {
        // given -- every field the Connector row needs is on the transaction body itself, so no state change is
        // needed
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var body = recordItem.getTransactionBody().getClprRegisterConnector();
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprConnector.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprConnector(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getAdminKey().toByteString()), ClprConnector::getAdminKey)
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(EntityId.of(body.getConnectorContract()), ClprConnector::getConnectorContractId)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(consensusTimestamp, ClprConnector::getCreatedTimestamp)
                .returns(body.getLockedStake(), ClprConnector::getLockedStake)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange);
    }
}
