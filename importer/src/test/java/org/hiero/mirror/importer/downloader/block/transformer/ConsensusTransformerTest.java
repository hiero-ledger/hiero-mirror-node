// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Topic;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsensusTransformerTest extends AbstractTransformerTest {

    @Test
    void consensusCreateTopicTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.consensusCreateTopic(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusCreateTopicTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .receipt(TransactionReceipt.Builder::clearTopicID)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.consensusCreateTopic(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void consensusSubmitMessageTransform(boolean assessedCustomFees) {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .record(r -> {
                    if (assessedCustomFees) {
                        r.addAssessedCustomFees(recordItemBuilder.assessedCustomFee())
                                .addAssessedCustomFees(recordItemBuilder.assessedCustomFee());
                    } else {
                        r.clearAssessedCustomFees();
                    }
                })
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .consensusSubmitMessage(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusSubmitMessageTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .receipt(r ->
                        r.clearTopicRunningHash().clearTopicRunningHashVersion().clearTopicSequenceNumber())
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .consensusSubmitMessage(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void scheduledConsensusSubmitMessageTransform() {
        // given
        var scheduledTransactionId = TransactionID.newBuilder()
                .setAccountID(recordItemBuilder.accountId())
                .setScheduled(true)
                .setTransactionValidStart(recordItemBuilder.timestamp())
                .build();
        var topicId = recordItemBuilder.topicId();
        var runningHash = recordItemBuilder.bytes(48);
        var scheduleSignRecordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduledTransactionID(scheduledTransactionId))
                .customize(this::finalize)
                .build();
        var scheduleSignBlockTransaction = blockTransactionBuilder
                .scheduleCreate(scheduleSignRecordItem)
                .stateChanges(s -> {
                    var stateChanges = s.getFirst().toBuilder()
                            .addStateChanges(StateChange.newBuilder()
                                    .setStateId(StateIdentifier.STATE_ID_TOPICS_VALUE)
                                    .setMapUpdate(MapUpdateChange.newBuilder()
                                            .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId))
                                            .setValue(MapChangeValue.newBuilder()
                                                    .setTopicValue(Topic.newBuilder()
                                                            .setTopicId(topicId)
                                                            .setRunningHash(runningHash)
                                                            .setSequenceNumber(10)))))
                            .build();
                    s.clear();
                    s.add(stateChanges);
                })
                .build();
        var consensusSubmitMessageRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .clearIncrementer()
                .receipt(r -> r.setTopicRunningHash(runningHash).setTopicSequenceNumber(10))
                .transactionBody(b -> b.setTopicID(topicId))
                .transactionBodyWrapper(w -> w.setTransactionID(scheduledTransactionId))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var consensusSubmitMessageBlockTransaction = blockTransactionBuilder
                .consensusSubmitMessage(consensusSubmitMessageRecordItem)
                .previous(scheduleSignBlockTransaction)
                .stateChanges(List::clear)
                .trigger(scheduleSignBlockTransaction)
                .build();
        var blockFile = blockFileBuilder
                .items(List.of(scheduleSignBlockTransaction, consensusSubmitMessageBlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(scheduleSignRecordItem, consensusSubmitMessageRecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }
}
