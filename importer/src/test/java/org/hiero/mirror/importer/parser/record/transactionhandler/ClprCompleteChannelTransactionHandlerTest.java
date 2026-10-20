// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprCompleteChannelTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprCompleteChannelTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprCompleteChannelTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprCompleteChannel(ClprCompleteChannelTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setPublicKey(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setSignature(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme.ED25519)
                        .setVerifierContract(domainBuilder.entityNum(3000L).toContractID())
                        .setConfigProofBytes(DomainUtils.fromBytes(domainBuilder.bytes(16)))
                        .setEndpointManifestProofBytes(DomainUtils.fromBytes(domainBuilder.bytes(16))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var body = transactionBody.getClprCompleteChannel();
        var channelId = body.getChannelId();
        // verifierContract deliberately differs from the tx body's (3000L) to prove it's read from state
        var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setChainId("hedera:mainnet")
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .setNextMessageId(1)
                .setAckedMessageId(0)
                .setSentRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setReceivedMessageId(0)
                .setReceivedRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setOwnershipCommitment(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setVerifierFingerprint(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setVerifierContract(domainBuilder.entityNum(4000L).toContractID())
                .build();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprChannelValue);
        var transaction = domainBuilder.transaction().get();
        var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        var commitmentCaptor = ArgumentCaptor.forClass(ClprChannelPendingCommitment.class);
        var verifierContractId = EntityId.of(clprChannelValue.getVerifierContract());

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(DomainUtils.toBytes(channelId), ClprChannel::getChannelId)
                .returns(consensusTimestamp, ClprChannel::getCreatedTimestamp)
                .returns("hedera:mainnet", ClprChannel::getChainId)
                .returns(verifierContractId, ClprChannel::getVerifierContractId)
                .returns(
                        DomainUtils.toBytes(clprChannelValue.getVerifierFingerprint()),
                        ClprChannel::getVerifierFingerprint)
                .returns(ClprChannelStatus.ACTIVE, ClprChannel::getStatus)
                .returns(1L, ClprChannel::getNextMessageId)
                .returns(0L, ClprChannel::getAckedMessageId)
                .returns(0L, ClprChannel::getReceivedMessageId)
                .returns(DomainUtils.toBytes(body.getPublicKey()), ClprChannel::getPublicKey)
                .returns(DomainUtils.toBytes(body.getSignature()), ClprChannel::getSignature)
                .returns(ClprSignatureScheme.ED25519, ClprChannel::getSignatureScheme)
                .returns(DomainUtils.toBytes(body.getConfigProofBytes()), ClprChannel::getConfigProofBytes)
                .returns(
                        DomainUtils.toBytes(body.getEndpointManifestProofBytes()),
                        ClprChannel::getEndpointManifestProofBytes)
                .returns(Range.atLeast(consensusTimestamp), ClprChannel::getTimestampRange);

        verify(entityListener).onClprChannelPendingCommitment(commitmentCaptor.capture());
        assertThat(commitmentCaptor.getValue())
                .returns(
                        DomainUtils.toBytes(clprChannelValue.getOwnershipCommitment()),
                        ClprChannelPendingCommitment::getOwnershipCommitment)
                .returns(consensusTimestamp, ClprChannelPendingCommitment::getCompletedTimestamp);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, verifierContractId));
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
            com.hederahashgraph.api.proto.java.ClprChannel clprChannel) {
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
                .clprChannel(clprChannel)
                .build();
    }
}
