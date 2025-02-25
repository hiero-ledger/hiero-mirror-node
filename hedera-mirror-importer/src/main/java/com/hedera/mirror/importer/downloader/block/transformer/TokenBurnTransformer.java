// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class TokenBurnTransformer extends AbstractTokenTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        updateTotalSupply(blockItem.stateChanges(), transactionRecordBuilder);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENBURN;
    }
}
