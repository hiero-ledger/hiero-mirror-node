// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprCompleteConnectorTransactionBody;
import com.hederahashgraph.api.proto.java.ClprSignatureScheme;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.SignatureUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                        .setSignatureScheme(ClprSignatureScheme.ECDSA_SECP256K1)
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
        var recordItem = getRecordItem(
                getDefaultTransactionBody().build(),
                getDefaultTransactionRecord().build());
        var body = recordItem.getTransactionBody().getClprCompleteConnector();
        var transaction = domainBuilder.transaction().get();
        var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        var commitmentCaptor = ArgumentCaptor.forClass(ClprConnectorPendingCommitment.class);
        var connectorContractId = EntityId.of(body.getConnectorContract());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var timestampRange = Range.atLeast(consensusTimestamp);

        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(connectorContractId, ClprConnector::getConnectorContractId)
                .returns(body.getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(body.getLockedStake(), ClprConnector::getLockedStake)
                .returns(consensusTimestamp, ClprConnector::getCreatedTimestamp)
                .returns(timestampRange, ClprConnector::getTimestampRange);

        verify(entityListener).onClprConnectorPendingCommitment(commitmentCaptor.capture());
        var expectedCommitment = SignatureUtils.keccak256(
                DomainUtils.toBytes(body.getConnectorId()), DomainUtils.toBytes(body.getPublicKey()));
        assertThat(commitmentCaptor.getValue())
                .returns(expectedCommitment, ClprConnectorPendingCommitment::getCommitment)
                .returns(consensusTimestamp, ClprConnectorPendingCommitment::getCompletedTimestamp)
                .returns(DomainUtils.toBytes(body.getPublicKey()), ClprConnectorPendingCommitment::getPublicKey)
                .returns(DomainUtils.toBytes(body.getSalt()), ClprConnectorPendingCommitment::getSalt)
                .returns(DomainUtils.toBytes(body.getSignature()), ClprConnectorPendingCommitment::getSignature)
                .returns(
                        org.hiero.mirror.common.domain.clpr.ClprSignatureScheme.ECDSA_SECP256K1,
                        ClprConnectorPendingCommitment::getSignatureScheme)
                .returns(timestampRange, ClprConnectorPendingCommitment::getTimestampRange);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, connectorContractId));
    }
}
