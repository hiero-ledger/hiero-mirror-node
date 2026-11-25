// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.stateOverride;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class ContractCallContextTest {

    @Test
    void testGet() {
        var context = ContractCallContext.get();
        assertThat(ContractCallContext.get()).isEqualTo(context);
    }

    @Test
    void testReset() {
        var context = ContractCallContext.get();
        context.setBlockSupplier(() -> RecordFile.builder().consensusEnd(123L).build());
        context.reset();
    }

    @Test
    void resetDiscardsWritesAndKeepsReads() {
        final var context = ContractCallContext.get();
        context.getReadCacheState(1).put("read", "database");
        context.getWriteCacheState(1).put("updated", "value");
        context.getWriteCacheState(2).put("deleted", null);

        context.reset();

        assertThat(context.getWriteCacheState(1)).isEmpty();
        assertThat(context.getWriteCacheState(2)).isEmpty();
        assertThat(context.getReadCacheState(1)).containsEntry("read", "database");
    }

    @Test
    void cachesAreKeptPerStateId() {
        final var context = ContractCallContext.get();
        final int largeStateId = 10_001;
        context.getReadCacheState(1).put("key", "small");
        context.getReadCacheState(largeStateId).put("key", "large");
        context.getWriteCacheState(largeStateId).put("key", "written");

        assertThat(context.getReadCacheState(1)).containsOnly(Map.entry("key", "small"));
        assertThat(context.getReadCacheState(largeStateId)).containsOnly(Map.entry("key", "large"));
        assertThat(context.getWriteCacheState(1)).isEmpty();
        assertThat(context.getWriteCacheState(largeStateId)).containsOnly(Map.entry("key", "written"));

        context.reset();

        assertThat(context.getWriteCacheState(largeStateId)).isEmpty();
        assertThat(context.getReadCacheState(largeStateId)).containsOnly(Map.entry("key", "large"));
    }

    @Test
    void testGetTimestampNonHistorical() {
        var context = ContractCallContext.get();
        context.setTimestamp(Optional.of(123L));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();
        assertThat(context.getTimestampForSystemFiles()).isEmpty();
    }

    @Test
    void testGetTimestampForActionsReturnsSetTimestamp() {
        var context = ContractCallContext.get();
        var timestamp = 123L;
        context.setApi(ApiEndpointName.ACTIONS);
        context.setTimestamp(Optional.of(timestamp));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(timestamp));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(timestamp + 1));
    }

    @Test
    void testGetTimestampOpcodeReplay() {
        var context = ContractCallContext.get();
        var previousBlockTimestamp = 122L;
        var consensusTimestamp = 123L;
        context.setTimestamp(Optional.of(previousBlockTimestamp));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();
        assertThat(context.getTimestampForSystemFiles()).isEmpty();

        var opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false),
                0,
                new OpcodesProperties());
        context.setOpcodeContext(opcodeContext);

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(previousBlockTimestamp));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(consensusTimestamp));
    }

    @Test
    void testGetTimestampHistorical() {
        var context = ContractCallContext.get();
        var timestamp = 123L;
        context.setTimestamp(Optional.of(timestamp));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.EARLIEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(timestamp));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(timestamp + 1));
    }

    @Test
    void testGetTimestampForHistoricalActionsFallsBackToRecordFile() {
        var context = ContractCallContext.get();
        var consensusEnd = 1_786_518_658_483_854_104L;
        context.setApi(ApiEndpointName.ACTIONS);
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.of("39156482"))
                .callData(new byte[0])
                .gasPrice(0L)
                .build());
        context.setBlockSupplier(() ->
                RecordFile.builder().consensusEnd(consensusEnd).index(39156482L).build());

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(consensusEnd));
        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(consensusEnd + 1));
    }

    @Test
    void testGetTimestampForSystemFilesFallsBackToRecordFileWhenNotOpcodeReplay() {
        var context = ContractCallContext.get();
        var consensusEnd = 1_786_518_658_483_854_104L;
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.of("39156482"))
                .callData(new byte[0])
                .gasPrice(0L)
                .build());
        context.setBlockSupplier(() ->
                RecordFile.builder().consensusEnd(consensusEnd).index(39156482L).build());

        assertThat(context.getTimestampForSystemFiles()).isEqualTo(Optional.of(consensusEnd + 1));
    }

    @Test
    void applyStateOverridesReplacesPreviousAndClearsWhenEmpty() {
        final var context = ContractCallContext.get();
        final var first = stateOverride("00000000000000000000000000000000000004e4");
        final var second = stateOverride("00000000000000000000000000000000000004e5");

        context.applyStateOverrides(List.of(first));
        assertThat(context.getStateOverrides()).containsValue(first);

        context.applyStateOverrides(List.of());
        assertThat(context.getStateOverrides()).isNull();

        context.applyStateOverrides(null);
        assertThat(context.getStateOverrides()).isNull();

        context.applyStateOverrides(List.of(first));
        context.applyStateOverrides(List.of(second));
        assertThat(context.getStateOverrides()).containsValue(second).doesNotContainValue(first);
    }

    @Test
    void blockOverrideDefaultsToEmpty() {
        assertThat(ContractCallContext.get().getBlockOverride()).isSameAs(BlockOverride.EMPTY);
    }

    @Test
    void setBlockOverrideDropsCachedBlockStatesWhenItChanges() {
        final var context = ContractCallContext.get();
        final var override = BlockOverride.builder().blockNumber(80L).build();
        cacheBlockStates(context);

        context.setBlockOverride(BlockOverride.EMPTY);

        assertThat(context.getReadCacheState(BLOCKS_STATE_ID)).isNotEmpty();
        assertThat(context.getWriteCacheState(BLOCK_STREAM_INFO_STATE_ID)).isNotEmpty();

        context.setBlockOverride(override);

        assertThat(context.getBlockOverride()).isSameAs(override);
        assertThat(context.getReadCacheState(BLOCKS_STATE_ID)).isEmpty();
        assertThat(context.getWriteCacheState(BLOCKS_STATE_ID)).isEmpty();
        assertThat(context.getReadCacheState(BLOCK_STREAM_INFO_STATE_ID)).isEmpty();
        assertThat(context.getWriteCacheState(BLOCK_STREAM_INFO_STATE_ID)).isEmpty();
        assertThat(context.getReadCacheState(1)).containsEntry("key", "other");

        cacheBlockStates(context);
        context.setBlockOverride(BlockOverride.builder().blockNumber(80L).build());

        assertThat(context.getReadCacheState(BLOCKS_STATE_ID)).isNotEmpty();
    }

    private static void cacheBlockStates(final ContractCallContext context) {
        context.getReadCacheState(1).put("key", "other");
        context.getReadCacheState(BLOCKS_STATE_ID).put("key", "block");
        context.getWriteCacheState(BLOCKS_STATE_ID).put("key", "block");
        context.getReadCacheState(BLOCK_STREAM_INFO_STATE_ID).put("key", "stream");
        context.getWriteCacheState(BLOCK_STREAM_INFO_STATE_ID).put("key", "stream");
    }
}
