// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class BlockInfoSingletonTest {

    private final BlockInfoSingleton blockInfoSingleton = new BlockInfoSingleton();
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordFile recordFile = domainBuilder.recordFile().get();

    @Test
    void get() {
        ContractCallContext.get().setBlockSupplier(() -> recordFile);
        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(recordFile.getConsensusEnd()))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile.getConsensusStart()))
                        .lastBlockNumber(recordFile.getIndex() - 1)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void getUsesBlockOverrideNumber() {
        final var context = ContractCallContext.get();
        context.setBlockSupplier(() -> recordFile);
        context.setBlockOverride(BlockOverride.builder().blockNumber(42L).build());

        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(recordFile.getConsensusEnd()))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(recordFile.getConsensusStart()))
                        .lastBlockNumber(41L)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void getUsesBlockOverrideTime() {
        final var context = ContractCallContext.get();
        context.setBlockSupplier(() -> recordFile);
        final var timeNanos = 1_710_858_432_000_000_000L;
        context.setBlockOverride(BlockOverride.builder().time(1_710_858_432L).build());
        final long blockPeriodNanos = recordFile.getConsensusEnd() - recordFile.getConsensusStart();

        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(timeNanos + blockPeriodNanos))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(timeNanos))
                        .lastBlockNumber(recordFile.getIndex() - 1)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void getEndsAnOverriddenBlockAtTheLatestRepresentableTime() {
        final var context = ContractCallContext.get();
        final var longBlock = RecordFile.builder()
                .consensusEnd(10_000_000_000L)
                .consensusStart(0L)
                .index(5L)
                .build();
        context.setBlockSupplier(() -> longBlock);
        context.setBlockOverride(
                BlockOverride.builder().time(BlockOverride.MAX_TIME).build());
        final var timeNanos = BlockOverride.MAX_TIME * 1_000_000_000L;

        assertThat(blockInfoSingleton.get())
                .isEqualTo(BlockInfo.newBuilder()
                        .blockHashes(Bytes.EMPTY)
                        .consTimeOfLastHandledTxn(convertToTimestamp(Long.MAX_VALUE))
                        .firstConsTimeOfCurrentBlock(convertToTimestamp(timeNanos))
                        .lastBlockNumber(4L)
                        .migrationRecordsStreamed(true)
                        .build());
    }

    @Test
    void key() {
        assertThat(blockInfoSingleton.getStateId()).isEqualTo(BLOCKS_STATE_ID);
    }

    @Test
    void set() {
        ContractCallContext.get().setBlockSupplier(() -> recordFile);
        blockInfoSingleton.set(BlockInfo.DEFAULT);
        assertThat(blockInfoSingleton.get()).isNotEqualTo(BlockInfo.DEFAULT);
    }
}
