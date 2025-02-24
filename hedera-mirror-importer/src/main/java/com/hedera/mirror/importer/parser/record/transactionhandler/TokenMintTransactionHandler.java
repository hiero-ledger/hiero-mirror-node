// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class TokenMintTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenMint().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenMint();
        var tokenId = transaction.getEntityId();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long newTotalSupply = recordItem.getTransactionRecord().getReceipt().getNewTotalSupply();

        var token = new Token();
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(newTotalSupply);
        entityListener.onToken(token);

        var serialNumbers = recordItem.getTransactionRecord().getReceipt().getSerialNumbersList();
        for (int i = 0; i < serialNumbers.size(); i++) {
            if (i >= transactionBody.getMetadataCount()) {
                Utility.handleRecoverableError(
                        "Mismatch between {} metadata and {} serial numbers at {}",
                        transactionBody.getMetadataCount(),
                        serialNumbers,
                        consensusTimestamp);
                break;
            }

            var nft = Nft.builder()
                    .createdTimestamp(consensusTimestamp)
                    .deleted(false)
                    .metadata(toBytes(transactionBody.getMetadata(i)))
                    .serialNumber(serialNumbers.get(i))
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .tokenId(tokenId.getId())
                    .build();
            entityListener.onNft(nft);
        }
    }
}
