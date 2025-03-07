// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class CryptoAddLiveHashTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(getLiveHash(recordItem).getAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOADDLIVEHASH;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isClaims() || !recordItem.isSuccessful()) {
            return;
        }

        var liveHash = new LiveHash();
        liveHash.setConsensusTimestamp(transaction.getConsensusTimestamp());
        liveHash.setLivehash(toBytes(getLiveHash(recordItem).getHash()));
        entityListener.onLiveHash(liveHash);
    }

    @SuppressWarnings("deprecation")
    private com.hederahashgraph.api.proto.java.LiveHash getLiveHash(RecordItem recordItem) {
        return recordItem.getTransactionBody().getCryptoAddLiveHash().getLiveHash();
    }
}
