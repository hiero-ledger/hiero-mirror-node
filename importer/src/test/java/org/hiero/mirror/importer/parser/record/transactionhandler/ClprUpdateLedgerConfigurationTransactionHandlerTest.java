// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.ClprUpdateLedgerConfigurationTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprUpdateLedgerConfigurationTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprUpdateLedgerConfigurationTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprUpdateLedgerConfiguration(ClprUpdateLedgerConfigurationTransactionBody.newBuilder()
                        .setConfiguration(com.hederahashgraph.api.proto.java.ClprLedgerConfiguration.newBuilder()
                                .setProtocolVersion(1)
                                .setChainId("hedera:mainnet")
                                .setServiceAddress(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                                .setInitialTrustAnchor(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                                .setInitialTrustAnchorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                                .setThrottles(ClprThrottles.newBuilder()
                                        .setMaxMessagesPerBundle(100)
                                        .setMaxMessagePayloadBytes(8192)
                                        .setMaxGasPerMessage(15_000_000L)
                                        .setMaxQueueDepth(1000)
                                        .setMaxSyncBytes(4_194_304L)
                                        .setMaxLocalEndpoints(39)
                                        .setMaxPeerEndpoints(39))));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given -- state-change value diverges from the submitted body's protocol_version/chain_id
        var transactionBody = getDefaultTransactionBody().build();
        var configuration = com.hederahashgraph.api.proto.java.ClprLedgerConfiguration.newBuilder()
                .setProtocolVersion(2)
                .setChainId("hedera:testnet")
                .setServiceAddress(DomainUtils.fromBytes(domainBuilder.bytes(20)))
                .setInitialTrustAnchor(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setInitialTrustAnchorId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setThrottles(ClprThrottles.newBuilder()
                        .setMaxMessagesPerBundle(200)
                        .setMaxMessagePayloadBytes(16384)
                        .setMaxGasPerMessage(30_000_000L)
                        .setMaxQueueDepth(2000)
                        .setMaxSyncBytes(8_388_608L)
                        .setMaxLocalEndpoints(78)
                        .setMaxPeerEndpoints(78))
                .build();
        var throttles = configuration.getThrottles();
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), configuration);
        var transaction = domainBuilder.transaction().get();
        var captor = ArgumentCaptor.forClass(ClprLedgerConfiguration.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener).onClprLedgerConfiguration(captor.capture());
        assertThat(captor.getValue())
                .returns(recordItem.getConsensusTimestamp(), ClprLedgerConfiguration::getConsensusTimestamp)
                .returns((long) configuration.getProtocolVersion(), ClprLedgerConfiguration::getProtocolVersion)
                .returns(configuration.getChainId(), ClprLedgerConfiguration::getChainId)
                .returns(
                        DomainUtils.toBytes(configuration.getServiceAddress()),
                        ClprLedgerConfiguration::getServiceAddress)
                .returns(
                        DomainUtils.toBytes(configuration.getInitialTrustAnchor()),
                        ClprLedgerConfiguration::getInitialTrustAnchor)
                .returns(
                        DomainUtils.toBytes(configuration.getInitialTrustAnchorId()),
                        ClprLedgerConfiguration::getInitialTrustAnchorId)
                .returns(throttles.getMaxGasPerMessage(), ClprLedgerConfiguration::getMaxGasPerMessage)
                .returns((long) throttles.getMaxLocalEndpoints(), ClprLedgerConfiguration::getMaxLocalEndpoints)
                .returns(
                        (long) throttles.getMaxMessagePayloadBytes(),
                        ClprLedgerConfiguration::getMaxMessagePayloadBytes)
                .returns((long) throttles.getMaxMessagesPerBundle(), ClprLedgerConfiguration::getMaxMessagesPerBundle)
                .returns((long) throttles.getMaxPeerEndpoints(), ClprLedgerConfiguration::getMaxPeerEndpoints)
                .returns((long) throttles.getMaxQueueDepth(), ClprLedgerConfiguration::getMaxQueueDepth)
                .returns(throttles.getMaxSyncBytes(), ClprLedgerConfiguration::getMaxSyncBytes);
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
            com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
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
                .clprLedgerConfiguration(configuration)
                .build();
    }
}
