// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
final class ClprCreateChannelTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCREATECHANNEL;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var transactionBody = recordItem.getTransactionBody().getClprCreateChannel();
        final var clprContext = recordItem.getClprContext();
        final var entry = clprContext == null ? null : clprContext.getClprChannel();
        if (ClprStateChangeHandler.isMissingStateChange(
                entry, "clpr_channel_value", "ClprCreateChannel", recordItem.getConsensusTimestamp())) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var clprChannelValue = entry.value();
        final var verifierContractId = EntityId.of(clprChannelValue.getVerifierContract());

        final var builder = ClprStateChangeHandler.populateFromStateChange(entry);
        final var clprChannel = builder.createdTimestamp(consensusTimestamp)
                .chainId(clprChannelValue.getChainId())
                .verifierContractId(verifierContractId)
                .verifierFingerprint(DomainUtils.toBytes(clprChannelValue.getVerifierFingerprint()))
                .peerVerifier(DomainUtils.toBytes(transactionBody.getPeerVerifier()))
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .configProofBytes(DomainUtils.toBytes(transactionBody.getConfigProofBytes()))
                .endpointManifestProofBytes(DomainUtils.toBytes(transactionBody.getEndpointManifestProofBytes()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannel(clprChannel);
        recordItem.addEntityId(verifierContractId);
    }
}
