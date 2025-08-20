// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractBlockItemTransformer implements BlockItemTransformer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public void transform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        var transactionBody = blockTransactionTransformation.getTransactionBody();
        var transactionResult = blockTransaction.getTransactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var recordBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .addAllAssessedCustomFees(transactionResult.getAssessedCustomFeesList())
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setMemo(transactionBody.getMemo())
                .setReceipt(receiptBuilder)
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionHash(blockTransaction.getTransactionHash())
                .setTransactionID(transactionBody.getTransactionID())
                .setTransferList(transactionResult.getTransferList());

        if (transactionResult.hasParentConsensusTimestamp()) {
            recordBuilder.setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp());
        }

        if (transactionResult.hasScheduleRef()) {
            recordBuilder.setScheduleRef(transactionResult.getScheduleRef());
        }

        doTransform(blockTransactionTransformation);
    }

    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        // do nothing
    }
}
