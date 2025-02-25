// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

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
                .delegatingSpender(EntityId.UNSET)
                .spender(EntityId.UNSET)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(tokenId.getId());

        var serialNumbers = transactionBody.getSerialNumbersList();
        for (int i = 0; i < serialNumbers.size(); i++) {
            var nft = nftBuilder.serialNumber(serialNumbers.get(i)).build();
            entityListener.onNft(nft);
        }
    }
}
