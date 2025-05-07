// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
class CryptoDeleteLiveHashTransactionHandler extends AbstractTransactionHandler {

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETELIVEHASH;
    }

    @Override
    @SuppressWarnings("deprecation")
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getCryptoDeleteLiveHash().getAccountOfLiveHash());
    }
}
