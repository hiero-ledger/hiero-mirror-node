// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
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
    void testGetTimestampNonHistorical() {
        var context = ContractCallContext.get();
        context.setTimestamp(Optional.of(123L));
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();
    }

    @Test
    void finishStorageDiscoveryCopiesSlotKeysAndClearsReadCache() {
        final var context = ContractCallContext.get();
        final var contractId =
                ContractID.newBuilder().shardNum(0).realmNum(0).contractNum(1).build();
        final var entityId = EntityId.of(0, 0, 1);
        final var slotKey = new SlotKey(contractId, Bytes.wrap(new byte[32]));

        context.getReadCacheState(ContractStorageReadableKVState.STATE_ID).put(slotKey, Bytes.EMPTY);
        context.finishStorageDiscovery(ContractStorageReadableKVState.STATE_ID);

        assertThat(context.isStorageDiscoveryModeFinished()).isTrue();
        assertThat(context.getDiscoveredStorageSlotKeys(entityId))
                .containsExactly(slotKey.key().toByteArray());
        assertThat(context.getReadCacheState(ContractStorageReadableKVState.STATE_ID))
                .isEmpty();
    }

    @Test
    void testGetTimestampOpcodeReplay() {
        var context = ContractCallContext.get();
        var timestamp = 123L;
        context.setTimestamp(Optional.of(timestamp));
        context.setOpcodeContext(null);
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        assertThat(context.getTimestamp()).isEmpty();

        context.setOpcodeContext(new org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext(
                new org.hiero.mirror.web3.service.model.OpcodeRequest(
                        new org.hiero.mirror.web3.common.TransactionIdParameter(
                                org.hiero.mirror.common.domain.entity.EntityId.EMPTY, java.time.Instant.EPOCH),
                        false,
                        false,
                        false),
                0));

        assertThat(context.getTimestamp()).isEqualTo(Optional.of(timestamp));
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
    }

    @Test
    void testGetTimestampIsCachedAfterFirstSuccessfulResolution() {
        // Given a historical context resolved via the block supplier
        var context = ContractCallContext.get();
        final long blockTimestamp = 999L;
        context.setBlockSupplier(
                () -> RecordFile.builder().consensusEnd(blockTimestamp).build());
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.EARLIEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        // When the timestamp is resolved the first time
        final var first = context.getTimestamp();
        assertThat(first).isEqualTo(Optional.of(blockTimestamp));

        // When the block supplier is swapped out (simulating prepareCallContext being called again)
        context.setBlockSupplier(
                () -> RecordFile.builder().consensusEnd(blockTimestamp + 1000L).build());

        // Then the cached value is returned – not recomputed from the new supplier
        assertThat(context.getTimestamp()).isEqualTo(first);
    }

    @Test
    void testGetTimestampRetriesIfFirstResolutionReturnsEmpty() {
        // Given a historical context where the block supplier initially returns null
        var context = ContractCallContext.get();
        context.setBlockSupplier(() -> null);
        context.setCallServiceParameters(ContractExecutionParameters.builder()
                .block(BlockType.EARLIEST)
                .callData(new byte[0])
                .gasPrice(0L)
                .build());

        // The first call returns empty (RecordFile not available yet)
        assertThat(context.getTimestamp()).isEmpty();

        // When the block supplier is updated to return a real RecordFile
        final long blockTimestamp = 777L;
        context.setBlockSupplier(
                () -> RecordFile.builder().consensusEnd(blockTimestamp).build());

        // Then the next call resolves and caches the timestamp
        assertThat(context.getTimestamp()).isEqualTo(Optional.of(blockTimestamp));

        // And subsequent calls continue to return the cached value
        context.setBlockSupplier(
                () -> RecordFile.builder().consensusEnd(blockTimestamp + 1000L).build());
        assertThat(context.getTimestamp()).isEqualTo(Optional.of(blockTimestamp));
    }
}
