// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprUpdateLedgerConfigurationTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRUPDATELEDGERCONFIGURATION;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var configuration = recordItem.getClprLedgerConfiguration();
        if (configuration == null) {
            Utility.handleRecoverableError(
                    "No clpr_ledger_configuration_value state change found for successful"
                            + " ClprUpdateLedgerConfiguration at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();

        var builder = org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration.builder()
                .consensusTimestamp(consensusTimestamp)
                .protocolVersion(configuration.getProtocolVersion())
                .chainId(configuration.getChainId())
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()));

        if (configuration.hasThrottles()) {
            var throttles = configuration.getThrottles();
            builder.maxGasPerMessage(throttles.getMaxGasPerMessage())
                    .maxLocalEndpoints((long) throttles.getMaxLocalEndpoints())
                    .maxMessagePayloadBytes((long) throttles.getMaxMessagePayloadBytes())
                    .maxMessagesPerBundle((long) throttles.getMaxMessagesPerBundle())
                    .maxPeerEndpoints((long) throttles.getMaxPeerEndpoints())
                    .maxQueueDepth((long) throttles.getMaxQueueDepth())
                    .maxSyncBytes(throttles.getMaxSyncBytes());
        }

        entityListener.onClprLedgerConfiguration(builder.build());
    }
}
