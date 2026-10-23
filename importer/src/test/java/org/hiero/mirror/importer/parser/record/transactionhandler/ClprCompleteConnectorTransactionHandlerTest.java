// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprCompleteConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.SignatureUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprCompleteConnectorTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprCompleteConnectorTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprCompleteConnector(ClprCompleteConnectorTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setConnectorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setPublicKey(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setSignature(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme.ECDSA_SECP256K1)
                        .setSalt(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setConnectorContract(domainBuilder.entityNum(2000L).toContractID())
                        .setAdminKey(Key.newBuilder().setEd25519(DomainUtils.fromBytes(domainBuilder.key())))
                        .setLockedStake(100_000_000L));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var body = transactionBody.getClprCompleteConnector();

        // Fields deliberately differ from the tx body to prove the Connector is built from state, not the body.
        var clprConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setConnectorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setConnectorContract(domainBuilder.entityNum(3000L).toContractID())
                .setAdminKey(Key.newBuilder().setEd25519(DomainUtils.fromBytes(domainBuilder.key())))
                .setLockedStake(200_000_000L)
                .setSlashCount(2)
                .setInFlightMessageCount(5)
                .build();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprConnectorValue);
        var transaction = domainBuilder.transaction().get();
        var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        var commitmentCaptor = ArgumentCaptor.forClass(ClprConnectorPendingCommitment.class);
        var connectorContractId = EntityId.of(clprConnectorValue.getConnectorContract());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var timestampRange = Range.atLeast(consensusTimestamp);

        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(DomainUtils.toBytes(clprConnectorValue.getChannelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(clprConnectorValue.getConnectorId()), ClprConnector::getConnectorId)
                .returns(connectorContractId, ClprConnector::getConnectorContractId)
                .returns(clprConnectorValue.getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(clprConnectorValue.getLockedStake(), ClprConnector::getLockedStake)
                .returns(2L, ClprConnector::getSlashCount)
                .returns(5L, ClprConnector::getInFlightMessageCount)
                .returns(consensusTimestamp, ClprConnector::getCreatedTimestamp)
                .returns(DomainUtils.toBytes(body.getPublicKey()), ClprConnector::getPublicKey)
                .returns(DomainUtils.toBytes(body.getSalt()), ClprConnector::getSalt)
                .returns(DomainUtils.toBytes(body.getSignature()), ClprConnector::getSignature)
                .returns(ClprSignatureScheme.ECDSA_SECP256K1, ClprConnector::getSignatureScheme)
                .returns(timestampRange, ClprConnector::getTimestampRange);

        verify(entityListener).onClprConnectorPendingCommitment(commitmentCaptor.capture());
        var expectedCommitment = SignatureUtils.keccak256(
                DomainUtils.toBytes(clprConnectorValue.getConnectorId()), DomainUtils.toBytes(body.getPublicKey()));
        assertThat(commitmentCaptor.getValue())
                .returns(expectedCommitment, ClprConnectorPendingCommitment::getCommitment)
                .returns(consensusTimestamp, ClprConnectorPendingCommitment::getCompletedTimestamp)
                .returns(timestampRange, ClprConnectorPendingCommitment::getTimestampRange);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, connectorContractId));
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
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.hederahashgraph.api.proto.java.ClprConnector clprConnector) {
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
                .clprConnector(clprConnector)
                .build();
    }
}
