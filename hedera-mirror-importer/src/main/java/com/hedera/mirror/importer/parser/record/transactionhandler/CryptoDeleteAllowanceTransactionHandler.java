// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.contractlog.ApproveAllowanceIndexedContractLog;
import com.hedera.mirror.importer.parser.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class CryptoDeleteAllowanceTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final SyntheticContractLogService syntheticContractLogService;

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETEALLOWANCE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        for (var nftAllowance :
                recordItem.getTransactionBody().getCryptoDeleteAllowance().getNftAllowancesList()) {
            var ownerId = EntityId.of(nftAllowance.getOwner());
            var tokenId = EntityId.of(nftAllowance.getTokenId());

            ownerId = EntityId.isEmpty(ownerId) ? recordItem.getPayerAccountId() : ownerId;
            for (var serialNumber : nftAllowance.getSerialNumbersList()) {
                var nft = Nft.builder()
                        .serialNumber(serialNumber)
                        .timestampRange(Range.atLeast(consensusTimestamp))
                        .tokenId(tokenId.getId())
                        .build();
                entityListener.onNft(nft);
                syntheticContractLogService.create(new ApproveAllowanceIndexedContractLog(
                        recordItem, tokenId, ownerId, EntityId.EMPTY, serialNumber));
            }

            recordItem.addEntityId(ownerId);
            recordItem.addEntityId(tokenId);
        }
    }
}
