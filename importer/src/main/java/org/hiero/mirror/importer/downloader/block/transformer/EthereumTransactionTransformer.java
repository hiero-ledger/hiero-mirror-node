// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class EthereumTransactionTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        if (!recordBuilder.hasContractCallResult() && !recordBuilder.hasContractCreateResult()) {
            return;
        }

        var resultBuilder = recordBuilder.hasContractCallResult()
                ? recordBuilder.getContractCallResultBuilder()
                : recordBuilder.getContractCreateResultBuilder();
        if (resultBuilder.getGasUsed() == 0) {
            recordBuilder.getReceiptBuilder().clearContractID();
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }
}
