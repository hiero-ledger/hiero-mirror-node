// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class ContractCallTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
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
