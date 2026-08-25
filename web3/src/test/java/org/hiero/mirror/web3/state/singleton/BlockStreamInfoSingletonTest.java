// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.state.Utils.convertToTimestamp;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class BlockStreamInfoSingletonTest {

    private final BlockStreamInfoSingleton blockStreamInfoSingleton = new BlockStreamInfoSingleton();
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    @DisplayName("should return the correct state key")
    void key() {
        // when
        final var key = blockStreamInfoSingleton.getStateId();

        // then
        assertThat(key).isEqualTo(BLOCK_STREAM_INFO_STATE_ID);
    }

    @Test
    @DisplayName("does not return default instance")
    void set() {
        // when
        final var blockStreamInfo = BlockStreamInfo.newBuilder()
                .blockNumber(0)
                .inputTreeRootHash(Bytes.wrap(domainBuilder.bytes(20)))
                .build();
        blockStreamInfoSingleton.set(blockStreamInfo);

        // then
        assertThat(blockStreamInfoSingleton.get()).isNotEqualTo(BlockStreamInfo.DEFAULT);
        assertThat(blockStreamInfoSingleton.get())
                .isEqualTo(BlockStreamInfo.newBuilder()
                        .lastHandleTime(Timestamp.newBuilder().seconds(1).build())
                        .build());
    }

    @Test
    void getUsesRecordFileBlockTime() {
        final var recordFile = domainBuilder.recordFile().get();
        ContractCallContext.get().setBlockSupplier(() -> recordFile);

        final var blockTime = convertToTimestamp(recordFile.getConsensusStart());
        final var blockEndTime = convertToTimestamp(recordFile.getConsensusEnd());
        assertThat(blockStreamInfoSingleton.get())
                .isEqualTo(BlockStreamInfo.newBuilder()
                        .blockNumber(recordFile.getIndex())
                        .blockTime(blockTime)
                        .lastHandleTime(blockEndTime)
                        .blockEndTime(blockEndTime)
                        .build());
    }

    @Test
    void getCachesBlockStreamInfoOnContext() {
        final var recordFile = domainBuilder.recordFile().get();
        ContractCallContext.get().setBlockSupplier(() -> recordFile);

        final var first = blockStreamInfoSingleton.get();
        final var second = blockStreamInfoSingleton.get();

        assertThat(first).isSameAs(second).isSameAs(ContractCallContext.get().getBlockStreamInfo());
    }

    @Test
    void getDoesNotCacheFallback() {
        assertThat(blockStreamInfoSingleton.get()).isSameAs(blockStreamInfoSingleton.get());
        assertThat(ContractCallContext.get().getBlockStreamInfo()).isNull();

        final var recordFile = domainBuilder.recordFile().get();
        ContractCallContext.get().setBlockSupplier(() -> recordFile);

        assertThat(blockStreamInfoSingleton.get())
                .isNotEqualTo(BlockStreamInfo.newBuilder()
                        .lastHandleTime(Timestamp.newBuilder().seconds(1).build())
                        .build())
                .isEqualTo(BlockStreamInfo.newBuilder()
                        .blockNumber(recordFile.getIndex())
                        .blockTime(convertToTimestamp(recordFile.getConsensusStart()))
                        .lastHandleTime(convertToTimestamp(recordFile.getConsensusEnd()))
                        .blockEndTime(convertToTimestamp(recordFile.getConsensusEnd()))
                        .build());
    }
}
