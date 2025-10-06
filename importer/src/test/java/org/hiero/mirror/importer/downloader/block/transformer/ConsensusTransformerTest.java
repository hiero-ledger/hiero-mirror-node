// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
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
}
