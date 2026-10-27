// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractDeleteTransformer extends AbstractContractTransformer {

    @Override
    protected void doTransform(final BlockTransactionTransformation blockTransactionTransformation) {
        if (!blockTransactionTransformation.blockTransaction().isSuccessful()) {
            return;
        }

        final var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        final var contractId = blockTransactionTransformation
                .getTransactionBody()
                .getContractDeleteInstance()
                .getContractID();
        setReceiptContractId(contractId, receiptBuilder);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTDELETEINSTANCE;
    }
}
