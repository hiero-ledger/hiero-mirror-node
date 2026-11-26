// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ClprDeleteConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClprDeleteConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprDeleteConnectorTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprDeleteConnector(ClprDeleteConnectorTransactionBody.newBuilder()
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
        // given -- channel_id, connector_id, and stake_recipient are all on the transaction body, so no state
        // change is needed, only a stake_recipient lookup
        final var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        final var body = recordItem.getTransactionBody().getClprDeleteConnector();
        final var stakeRecipientId = EntityId.of(body.getStakeRecipient());
        when(entityIdService.lookup(body.getStakeRecipient())).thenReturn(Optional.of(stakeRecipientId));
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprConnector.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprConnector(captor.capture());
        assertThat(captor.getValue())
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(stakeRecipientId, ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, stakeRecipientId));
    }

    @Test
    void updateTransactionUnresolvedStakeRecipient() {
        // given -- an alias-form stake_recipient that entityIdService can't resolve
        final var aliasAccountId = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(domainBuilder.evmAddress()))
                .build();
        final var body = getDefaultTransactionBody();
        body.getClprDeleteConnectorBuilder().setStakeRecipient(aliasAccountId);
        final var recordItem =
                getRecordItem(body.build(), getDefaultTransactionRecord().build());
        when(entityIdService.lookup(aliasAccountId)).thenReturn(Optional.empty());
        final var transaction = domainBuilder.transaction().get();
        final var captor = ArgumentCaptor.forClass(ClprConnector.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then -- persist EntityId.EMPTY rather than the wrong, zero-derived account
        verify(entityListener).onClprConnector(captor.capture());
        assertThat(captor.getValue()).returns(EntityId.EMPTY, ClprConnector::getStakeRecipientId);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
