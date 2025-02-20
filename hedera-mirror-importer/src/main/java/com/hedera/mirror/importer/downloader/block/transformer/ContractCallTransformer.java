// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem.RecordItemBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class ContractCallTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        if (recordBuilder.getContractCallResult().hasContractID()) {
            recordBuilder
                    .getReceiptBuilder()
                    .setContractID(recordBuilder.getContractCallResult().getContractID());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCALL;
    }
}
