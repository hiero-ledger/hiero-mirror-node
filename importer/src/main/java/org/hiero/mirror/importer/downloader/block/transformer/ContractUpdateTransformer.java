// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractUpdateTransformer extends AbstractContractTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        var contractId = blockTransactionTransformation
                .getTransactionBody()
                .getContractUpdateInstance()
                .getContractID();
        resolveEvmAddress(contractId, receiptBuilder, blockTransaction.getStateChangeContext());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTUPDATEINSTANCE;
    }
}
