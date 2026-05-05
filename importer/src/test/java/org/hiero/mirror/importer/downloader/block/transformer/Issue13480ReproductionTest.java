// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.UInt64Value;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hederahashgraph.api.proto.java.RegisteredNode;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Issue13480ReproductionTest extends AbstractTransformerTest {

    private static final int STATE_ID_REGISTERED_NODES = 56;

    @Test
    void reproduceIssue() {
        // given
        long registeredNodeId = 123L;
        final var expectedRecordItem = recordItemBuilder
                .registeredNodeCreate()
                .receipt(r -> r.setRegisteredNodeId(registeredNodeId).setStatus(ResponseCodeEnum.SUCCESS))
                .customize(this::finalize)
                .build();

        // Manually build state changes with the new format (entity_number_key and registered_node_value)
        final var key = MapChangeKey.newBuilder()
                .setEntityNumberKey(UInt64Value.of(registeredNodeId))
                .build();
        final var value = MapChangeValue.newBuilder()
                .setRegisteredNodeValue(RegisteredNode.newBuilder().setRegisteredNodeId(registeredNodeId))
                .build();
        final var stateChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().setKey(key).setValue(value))
                .setStateId(STATE_ID_REGISTERED_NODES)
                .build();
        final var stateChanges =
                StateChanges.newBuilder().addStateChanges(stateChange).build();

        final var blockTransaction = blockTransactionBuilder
                .registeredNodeCreate(expectedRecordItem)
                .stateChanges(List.of(stateChanges))
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getTransactionRecord().getReceipt().getRegisteredNodeId())
                    .isEqualTo(registeredNodeId);
        });
    }

    @Test
    void shouldHandleMissingStateChangeGracefully() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .registeredNodeCreate()
                .receipt(r -> r.clearRegisteredNodeId().setStatus(ResponseCodeEnum.SUCCESS))
                .customize(this::finalize)
                .build();

        // No state changes provided
        final var blockTransaction = blockTransactionBuilder
                .registeredNodeCreate(expectedRecordItem)
                .stateChanges(List.of())
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getTransactionRecord().getReceipt().hasRegisteredNodeId())
                    .isFalse();
        });
    }
}
