/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.security.MessageDigest;

abstract class AbstractBlockItemTransformer implements BlockItemTransformer {

    private static final MessageDigest DIGEST = createSha384Digest();

    public void transform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        var transactionResult = blockItem.transactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var recordBuilder = recordItemBuilder
                .transactionRecordBuilder()
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
            recordBuilder.setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp());
        }

        if (transactionResult.hasScheduleRef()) {
            recordBuilder.setScheduleRef(transactionResult.getScheduleRef());
        }

        processContractCallOutput(blockItem, recordItemBuilder);
        doTransform(blockItem, recordItemBuilder, transactionBody);
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
        var outputs = blockItem.transactionOutputs();
        if (!outputs.containsKey(TransactionCase.CONTRACT_CALL)) {
            return;
        }

        var contractCall = outputs.get(TransactionCase.CONTRACT_CALL).getContractCall();
        recordItemBuilder.sidecarRecords(contractCall.getSidecarsList());
        if (contractCall.hasContractCallResult()) {
            var recordBuilder = recordItemBuilder.transactionRecordBuilder();
            recordBuilder.setContractCallResult(contractCall.getContractCallResult());
        }
    }

    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {}
}
