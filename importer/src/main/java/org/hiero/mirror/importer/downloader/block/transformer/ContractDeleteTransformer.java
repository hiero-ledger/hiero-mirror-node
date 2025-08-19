// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractDeleteTransformer extends AbstractContractTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockItem = blockTransactionTransformation.blockTransaction();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        var contractId = blockTransactionTransformation
                .getTransactionBody()
                .getContractDeleteInstance()
                .getContractID();
        resolveEvmAddress(contractId, receiptBuilder, blockItem.getStateChangeContext());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTDELETEINSTANCE;
    }
}
