// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem.RecordItemBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class ContractUpdateTransformer extends AbstractContractTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        var contractId = transactionBody.getContractUpdateInstance().getContractID();
        resolveEvmAddress(contractId, receiptBuilder, blockItem.getStateChangeContext());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTUPDATEINSTANCE;
    }
}
