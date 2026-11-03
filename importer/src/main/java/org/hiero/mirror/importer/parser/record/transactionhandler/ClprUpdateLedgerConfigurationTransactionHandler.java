// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

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

        var clprContext = recordItem.getClprContext();
        var configuration = clprContext != null ? clprContext.getClprLedgerConfiguration() : null;
        if (ClprStateChangeHandler.isMissingStateChange(
                configuration,
                "clpr_ledger_configuration_value",
                "ClprUpdateLedgerConfiguration",
                recordItem.getConsensusTimestamp())) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();

        var builder = ClprLedgerConfiguration.builder()
                .consensusTimestamp(consensusTimestamp)
                .protocolVersion(configuration.getProtocolVersion())
                .chainId(configuration.getChainId())
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()));

        var throttleValues = ClprThrottleValues.from(configuration);
        if (throttleValues != null) {
            builder.maxGasPerMessage(throttleValues.maxGasPerMessage())
                    .maxLocalEndpoints(throttleValues.maxLocalEndpoints())
                    .maxMessagePayloadBytes(throttleValues.maxMessagePayloadBytes())
                    .maxMessagesPerBundle(throttleValues.maxMessagesPerBundle())
                    .maxPeerEndpoints(throttleValues.maxPeerEndpoints())
                    .maxQueueDepth(throttleValues.maxQueueDepth())
                    .maxSyncBytes(throttleValues.maxSyncBytes());
        }

        entityListener.onClprLedgerConfiguration(builder.build());
    }
}
