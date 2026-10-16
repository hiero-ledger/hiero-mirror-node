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
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprCompleteChannelTransactionHandler extends AbstractClprChannelTransactionHandler {

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
        var channelId = DomainUtils.toBytes(transactionBody.getChannelId());
        var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        var verifierContractId = EntityId.of(transactionBody.getVerifierContract());

        var builder = ClprChannel.builder();
        populateFromStateChange(builder, clprChannelValue);
        var clprChannel = builder.channelId(channelId)
                .createdTimestamp(consensusTimestamp)
                .chainId(clprChannelValue.getChainId())
                .verifierContractId(verifierContractId)
                .verifierFingerprint(DomainUtils.toBytes(clprChannelValue.getVerifierFingerprint()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannel(clprChannel);
        recordItem.addEntityId(verifierContractId);

        // Consensus never removes the commitment from the pending-commitments map at completion; recomputing it here
        // lets the Pending Channels API exclude it without joining across tables.
        var ownershipCommitment = SignatureUtils.keccak256(channelId, publicKey);
        var commitment = ClprChannelPendingCommitment.builder()
                .ownershipCommitment(ownershipCommitment)
                .completedTimestamp(consensusTimestamp)
                .publicKey(publicKey)
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .configProofBytes(DomainUtils.toBytes(transactionBody.getConfigProofBytes()))
                .endpointManifestProofBytes(DomainUtils.toBytes(transactionBody.getEndpointManifestProofBytes()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannelPendingCommitment(commitment);
    }
}
