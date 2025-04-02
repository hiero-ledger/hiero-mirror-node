// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractBlockItemTransformer implements BlockItemTransformer {

    private static final MessageDigest DIGEST = createSha384Digest();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public void transform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        var transactionBody = blockItemTransformation.transactionBody();
        var transactionResult = blockItem.getTransactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var recordBuilder = blockItemTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .addAllAssessedCustomFees(transactionResult.getAssessedCustomFeesList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setMemo(transactionBody.getMemo())
                .setReceipt(receiptBuilder)
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionHash(
                        calculateTransactionHash(blockItem.getTransaction().getSignedTransactionBytes()))
                .setTransactionID(transactionBody.getTransactionID())
                .setTransferList(transactionResult.getTransferList());

        if (transactionResult.hasParentConsensusTimestamp()) {
            recordBuilder.setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp());
        }

        if (transactionResult.hasScheduleRef()) {
            recordBuilder.setScheduleRef(transactionResult.getScheduleRef());
        }

        processContractCallOutput(blockItem, blockItemTransformation.recordItemBuilder());
        doTransform(blockItemTransformation);
    }

    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        // do nothing
    }

    private ByteString calculateTransactionHash(ByteString signedTransactionBytes) {
        return DomainUtils.fromBytes(DIGEST.digest(DomainUtils.toBytes(signedTransactionBytes)));
    }

    /**
     * Process contract call transaction output. Contract call transaction output is special that every child
     * transaction of a contract call transaction can have one.
     *
     * @param blockItem
     * @param recordItemBuilder
     */
    private void processContractCallOutput(BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder) {
        blockItem
                .getTransactionOutput(TransactionCase.CONTRACT_CALL)
                .map(TransactionOutput::getContractCall)
                .ifPresent(contractCall -> {
                    recordItemBuilder.sidecarRecords(contractCall.getSidecarsList());

                    if (contractCall.hasContractCallResult()) {
                        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
                        recordBuilder.setContractCallResult(contractCall.getContractCallResult());
                    }
                });
    }
}
