// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class ContractDeleteTransformer extends AbstractContractTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockItemTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        var contractId = blockItemTransformation
                .transactionBody()
                .getContractDeleteInstance()
                .getContractID();
        resolveEvmAddress(contractId, receiptBuilder, blockItem.getStateChangeContext());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTDELETEINSTANCE;
    }
}
