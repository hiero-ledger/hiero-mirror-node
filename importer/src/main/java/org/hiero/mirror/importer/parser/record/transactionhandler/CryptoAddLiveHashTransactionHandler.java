// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.LiveHash;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

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
