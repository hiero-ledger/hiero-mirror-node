// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
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
        blockItem
                .getTransactionOutput(TransactionCase.ETHEREUM_CALL)
                .map(TransactionOutput::getEthereumCall)
                .ifPresent(ethereumCall -> {
                    var recordItemBuilder = blockItemTransformation.recordItemBuilder();
                    var recordBuilder = recordItemBuilder.transactionRecordBuilder();
                    recordBuilder.setEthereumHash(ethereumCall.getEthereumHash());
                    recordItemBuilder.sidecarRecords(ethereumCall.getSidecarsList());

                    var receiptBuilder = recordBuilder.getReceiptBuilder();
                    switch (ethereumCall.getEthResultCase()) {
                        case ETHEREUM_CALL_RESULT -> {
                            var result = ethereumCall.getEthereumCallResult();
                            recordBuilder.setContractCallResult(result);
                            setReceipt(result, receiptBuilder);
                        }
                        case ETHEREUM_CREATE_RESULT -> {
                            var result = ethereumCall.getEthereumCreateResult();
                            recordBuilder.setContractCreateResult(result);
                            setReceipt(result, receiptBuilder);
                        }
                        default ->
                            log.warn(
                                    "Unhandled eth_result case {} for transaction at {}",
                                    ethereumCall.getEthResultCase(),
                                    blockItem.getConsensusTimestamp());
                    }
                });
    }

    private void setReceipt(ContractFunctionResult result, TransactionReceipt.Builder receiptBuilder) {
        if (result.getGasUsed() > 0) {
            receiptBuilder.setContractID(result.getContractID());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }
}
