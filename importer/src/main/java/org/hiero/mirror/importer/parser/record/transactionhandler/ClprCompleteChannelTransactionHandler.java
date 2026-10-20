// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprCompleteChannelTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCOMPLETECHANNEL;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getClprCompleteChannel();
        var clprChannelValue = recordItem.getClprChannel();
        if (clprChannelValue == null) {
            Utility.handleRecoverableError(
                    "No clpr_channel_value state change found for successful ClprCompleteChannel at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        var verifierContractId = EntityId.of(clprChannelValue.getVerifierContract());

        var builder = ClprChannel.builder();
        ClprStateChangeHandler.populateFromStateChange(builder, clprChannelValue);
        var clprChannel = builder.createdTimestamp(consensusTimestamp)
                .chainId(clprChannelValue.getChainId())
                .verifierContractId(verifierContractId)
                .verifierFingerprint(DomainUtils.toBytes(clprChannelValue.getVerifierFingerprint()))
                .publicKey(publicKey)
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .configProofBytes(DomainUtils.toBytes(transactionBody.getConfigProofBytes()))
                .endpointManifestProofBytes(DomainUtils.toBytes(transactionBody.getEndpointManifestProofBytes()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannel(clprChannel);
        recordItem.addEntityId(verifierContractId);

        // Marks the pending commitment completed rather than deleting it (consensus never removes it); reuses
        // clprChannel's own ownership_commitment rather than recomputing it.
        var commitment = ClprChannelPendingCommitment.builder()
                .ownershipCommitment(clprChannel.getOwnershipCommitment())
                .completedTimestamp(consensusTimestamp)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannelPendingCommitment(commitment);
    }
}
