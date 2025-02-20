// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem.RecordItemBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class ContractCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.transactionOutputs().containsKey(TransactionCase.CONTRACT_CREATE)) {
            return;
        }

        var contractCreate = blockItem
                .transactionOutputs()
                .get(TransactionCase.CONTRACT_CREATE)
                .getContractCreate();
        recordItemBuilder.sidecarRecords(contractCreate.getSidecarsList());
        if (!contractCreate.hasContractCreateResult()) {
            return;
        }

        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        recordBuilder.setContractCreateResult(contractCreate.getContractCreateResult());
        if (contractCreate.getContractCreateResult().hasContractID()) {
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
