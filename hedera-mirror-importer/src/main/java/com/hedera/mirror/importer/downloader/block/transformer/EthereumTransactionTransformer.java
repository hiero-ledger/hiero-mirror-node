// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
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
            case ETHEREUM_CREATE_RESULT -> recordBuilder.setContractCreateResult(
                    ethereumCall.getEthereumCreateResult());
            default -> log.warn(
                    "Unhandled eth_result case {} for transaction at {}",
                    ethereumCall.getEthResultCase(),
                    blockItem.getConsensusTimestamp());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }
}
