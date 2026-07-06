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
}
