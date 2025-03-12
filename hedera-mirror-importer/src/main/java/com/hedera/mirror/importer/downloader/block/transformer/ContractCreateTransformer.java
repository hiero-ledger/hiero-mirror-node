// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class ContractCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.hasTransactionOutput(TransactionCase.CONTRACT_CREATE)) {
            return;
        }

        var recordItemBuilder = blockItemTransformation.recordItemBuilder();
        var contractCreate =
                blockItem.getTransactionOutput(TransactionCase.CONTRACT_CREATE).getContractCreate();
        recordItemBuilder.sidecarRecords(contractCreate.getSidecarsList());
        if (!contractCreate.hasContractCreateResult()) {
            return;
        }

        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        recordBuilder.setContractCreateResult(contractCreate.getContractCreateResult());
        if (contractCreate.getContractCreateResult().hasContractID() && blockItem.isSuccessful()) {
            recordBuilder
                    .getReceiptBuilder()
                    .setContractID(contractCreate.getContractCreateResult().getContractID());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCREATEINSTANCE;
    }
}
