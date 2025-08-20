// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        blockTransactionTransformation
                .blockTransaction()
                .getTransactionOutput(TransactionCase.CONTRACT_CREATE)
                .map(TransactionOutput::getContractCreate)
                .ifPresent(contractCreate -> {
                    //                    var recordItemBuilder = blockTransactionTransformation.recordItemBuilder();
                    //                    recordItemBuilder.sidecarRecords(contractCreate.getSidecarsList());
                    //                    if (!contractCreate.hasContractCreateResult()) {
                    //                        return;
                    //                    }
                    //
                    //                    var recordBuilder = recordItemBuilder.transactionRecordBuilder();
                    //
                    // recordBuilder.setContractCreateResult(contractCreate.getContractCreateResult());
                    //                    if (contractCreate.getContractCreateResult().hasContractID()) {
                    //                        recordBuilder
                    //                                .getReceiptBuilder()
                    //                                .setContractID(
                    //                                        contractCreate.getContractCreateResult().getContractID());
                    //                    }
                });
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCREATEINSTANCE;
    }
}
