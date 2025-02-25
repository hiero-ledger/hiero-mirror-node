// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.security.MessageDigest;

abstract class AbstractBlockItemTransformer implements BlockItemTransformer {

    private static final MessageDigest DIGEST = createSha384Digest();

    public TransactionRecord getTransactionRecord(BlockItem blockItem, TransactionBody transactionBody) {
        var transactionResult = blockItem.transactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var transactionRecordBuilder = TransactionRecord.newBuilder()
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setMemo(transactionBody.getMemo())
                .setReceipt(receiptBuilder)
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionHash(
                        calculateTransactionHash(blockItem.transaction().getSignedTransactionBytes()))
                .setTransactionID(transactionBody.getTransactionID())
                .setTransferList(transactionResult.getTransferList());

        if (transactionResult.hasParentConsensusTimestamp()) {
            transactionRecordBuilder.setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp());
        }
        if (transactionResult.hasScheduleRef()) {
            transactionRecordBuilder.setScheduleRef(transactionResult.getScheduleRef());
        }

        updateTransactionRecord(blockItem, transactionBody, transactionRecordBuilder);
        return transactionRecordBuilder.build();
    }

    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        // do nothing
    }

    private ByteString calculateTransactionHash(ByteString signedTransactionBytes) {
        return DomainUtils.fromBytes(DIGEST.digest(DomainUtils.toBytes(signedTransactionBytes)));
    }
}
