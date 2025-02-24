// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {

        if (!blockItem.successful()) {
            return;
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasSubmitMessage()) {
                var submitMessageOutput = transactionOutput.getSubmitMessage();
                var assessedCustomFees = submitMessageOutput.getAssessedCustomFeesList();
                transactionRecordBuilder.addAllAssessedCustomFees(assessedCustomFees);
                break;
            }
        }

        for (var stateChange : blockItem.stateChanges()) {
            for (var change : stateChange.getStateChangesList()) {
                if (change.getStateId() == StateIdentifier.STATE_ID_TOPICS.getNumber() && change.hasMapUpdate()) {
                    var value = change.getMapUpdate().getValue();
                    if (value.hasTopicValue()) {
                        var topicValue = value.getTopicValue();
                        transactionRecordBuilder
                                .getReceiptBuilder()
                                .setTopicRunningHash(topicValue.getRunningHash())
                                .setTopicSequenceNumber(topicValue.getSequenceNumber())
                                .setTopicRunningHashVersion(DEFAULT_RUNNING_HASH_VERSION);

                        return;
                    }
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
