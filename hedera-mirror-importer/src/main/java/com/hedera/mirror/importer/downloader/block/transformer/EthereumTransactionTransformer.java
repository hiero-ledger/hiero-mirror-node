// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import jakarta.inject.Named;

@Named
final class EthereumTransactionTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.hasTransactionOutput(TransactionCase.ETHEREUM_CALL)) {
            return;
        }

        var ethereumCall =
                blockItem.getTransactionOutput(TransactionCase.ETHEREUM_CALL).getEthereumCall();
        var recordItemBuilder = blockItemTransformation.recordItemBuilder();
        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        recordBuilder.setEthereumHash(ethereumCall.getEthereumHash());
        recordItemBuilder.sidecarRecords(ethereumCall.getSidecarsList());

        switch (ethereumCall.getEthResultCase()) {
            case ETHEREUM_CALL_RESULT -> recordBuilder.setContractCallResult(ethereumCall.getEthereumCallResult());
            case ETHEREUM_CREATE_RESULT -> {
                var result = ethereumCall.getEthereumCreateResult();
                recordBuilder.setContractCreateResult(result);
                setReceipt(result, recordBuilder.getReceiptBuilder());
            }
            default -> log.warn(
                    "Unhandled eth_result case {} for transaction at {}",
                    ethereumCall.getEthResultCase(),
                    blockItem.getConsensusTimestamp());
        }
    }

    private void setReceipt(ContractFunctionResult result, TransactionReceipt.Builder receiptBuilder) {
        if(result.getGasUsed() > 0) {
            receiptBuilder.setContractID(result.getContractID());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }
}
