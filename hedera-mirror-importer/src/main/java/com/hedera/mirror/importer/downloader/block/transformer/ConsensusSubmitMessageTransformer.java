// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.successful()) {
            return;
        }

        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        var submitMessageOutput = blockItem
                .transactionOutputs()
                .get(TransactionCase.SUBMIT_MESSAGE)
                .getSubmitMessage();
        recordBuilder.addAllAssessedCustomFees(submitMessageOutput.getAssessedCustomFeesList());

        stateChangeContext
                .getTopicMessage(transactionBody.getConsensusSubmitMessage().getTopicID())
                .ifPresentOrElse(
                        topicMessage -> recordBuilder
                                .getReceiptBuilder()
                                .setTopicRunningHash(DomainUtils.fromBytes(topicMessage.getRunningHash()))
                                .setTopicRunningHashVersion(DEFAULT_RUNNING_HASH_VERSION)
                                .setTopicSequenceNumber(topicMessage.getSequenceNumber()),
                        () -> log.warn(
                                "No topic message found in state changes for ConsensusSubmitMessage transaction at {}",
                                blockItem.consensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
