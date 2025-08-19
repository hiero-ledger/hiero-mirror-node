// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractCallTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
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
