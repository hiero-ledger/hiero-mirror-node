// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class TokenBurnTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenBurn().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENBURN;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenBurn();
        var tokenId = transaction.getEntityId();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long newTotalSupply = recordItem.getTransactionRecord().getReceipt().getNewTotalSupply();

        var token = new Token();
        token.setTotalSupply(newTotalSupply);
        token.setTokenId(tokenId.getId());
        entityListener.onToken(token);

        transactionBody.getSerialNumbersList().forEach(serialNumber -> {
            var nft = Nft.builder()
                    .deleted(true)
                    .serialNumber(serialNumber)
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .tokenId(tokenId.getId())
                    .build();
            entityListener.onNft(nft);
        });
    }
}
