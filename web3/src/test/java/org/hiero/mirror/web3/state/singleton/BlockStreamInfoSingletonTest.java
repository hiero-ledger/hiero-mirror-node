// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("should always return default instance")
    void set() {
        // when
        final var blockStreamInfo = BlockStreamInfo.newBuilder()
                .blockNumber(0)
                .inputTreeRootHash(Bytes.wrap(domainBuilder.bytes(20)))
                .build();
        blockStreamInfoSingleton.set(blockStreamInfo);

        // then
        assertThat(blockStreamInfoSingleton.get()).isEqualTo(BlockStreamInfo.DEFAULT);
    }
}
