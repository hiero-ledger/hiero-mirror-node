// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static java.util.function.Predicate.not;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
@RequiredArgsConstructor
class TokenWipeTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenWipe().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENWIPE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenWipe();
        var tokenId = transaction.getEntityId();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long newTotalSupply = recordItem.getTransactionRecord().getReceipt().getNewTotalSupply();

        var token = new Token();
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(newTotalSupply);
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

        var account = transactionBody.getAccount();
        entityIdService
                .lookup(account)
                .filter(not(EntityId::isEmpty))
                .ifPresentOrElse(
                        recordItem::addEntityId,
                        () -> Utility.handleRecoverableError(
                                "Invalid TokenWipe account {} at {}", account, consensusTimestamp));
    }
}
