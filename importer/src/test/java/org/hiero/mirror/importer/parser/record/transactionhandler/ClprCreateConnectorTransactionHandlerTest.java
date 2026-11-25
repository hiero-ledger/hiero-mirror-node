// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprCreateConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
final class ClprCreateConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        when(entityIdService.lookup(contractId, false)).thenReturn(Optional.of(defaultEntityId));
        return new ClprCreateConnectorTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprCreateConnector(ClprCreateConnectorTransactionBody.newBuilder()
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
        final var body = recordItem.getTransactionBody().getClprCreateConnector();
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(transactionHandler.getEntity(recordItem)))
                .get();
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
        verify(entityIdService).lookup(body.getConnectorContract(), false);
    }

    @Test
    void updateTransactionUnresolvedContract(CapturedOutput output) {
        // given
        final var evmContractId = ContractID.newBuilder()
                .setEvmAddress(DomainUtils.fromBytes(domainBuilder.evmAddress()))
                .build();
        final var body = getDefaultTransactionBody();
        body.getClprCreateConnectorBuilder().setConnectorContract(evmContractId);
        final var recordItem = getRecordItem(body.build(), getDefaultTransactionRecord().build());
        when(entityIdService.lookup(evmContractId, false)).thenReturn(Optional.empty());
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(transactionHandler.getEntity(recordItem)))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(transaction.getEntityId()).isEqualTo(EntityId.EMPTY);
        verifyNoInteractions(entityListener);
        verify(entityIdService).lookup(evmContractId, false);
        assertThat(output.getAll()).contains("Recoverable error. Unable to resolve connector contract");
    }

    @Test
    void updateTransactionUnsuccessfulUnresolvedContract(CapturedOutput output) {
        // given
        final var evmContractId = ContractID.newBuilder()
                .setEvmAddress(DomainUtils.fromBytes(domainBuilder.evmAddress()))
                .build();
        final var body = getDefaultTransactionBody();
        body.getClprCreateConnectorBuilder().setConnectorContract(evmContractId);
        final var recordItem = getRecordItem(
                body.build(),
                getDefaultTransactionRecord()
                        .setReceipt(getTransactionReceipt(ResponseCodeEnum.INVALID_CONTRACT_ID))
                        .build());
        when(entityIdService.lookup(evmContractId, false)).thenReturn(Optional.empty());

        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(transactionHandler.getEntity(recordItem)))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(transaction.getEntityId()).isEqualTo(EntityId.EMPTY);
        verify(entityIdService).lookup(evmContractId, false);
        verifyNoInteractions(entityListener);
        assertThat(output.getAll()).doesNotContain("Recoverable error.");
    }
}
