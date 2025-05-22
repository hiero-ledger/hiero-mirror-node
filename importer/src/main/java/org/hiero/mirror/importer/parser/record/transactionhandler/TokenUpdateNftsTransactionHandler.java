// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.token.AbstractNft.RETAIN_SPENDER;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class TokenUpdateNftsTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdateNfts().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENUPDATENFTS;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenUpdateNfts();

        // Since there's only one updatable field, this is a no-op. In the future if there's multiple fields we'll have
        // to rework this logic
        if (!transactionBody.hasMetadata()) {
            return;
        }

        var tokenId = transaction.getEntityId();
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var nftBuilder = Nft.builder()
                .metadata(toBytes(transactionBody.getMetadata().getValue()))
                .delegatingSpender(RETAIN_SPENDER)
                .spender(RETAIN_SPENDER)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(tokenId.getId());

        var serialNumbers = transactionBody.getSerialNumbersList();
        for (int i = 0; i < serialNumbers.size(); i++) {
            var nft = nftBuilder.serialNumber(serialNumbers.get(i)).build();
            entityListener.onNft(nft);
        }
    }
}
