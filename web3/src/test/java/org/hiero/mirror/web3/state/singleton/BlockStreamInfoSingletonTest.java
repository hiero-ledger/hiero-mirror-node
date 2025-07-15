// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.ContextExtension;
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
        final var key = blockStreamInfoSingleton.getKey();

        // then
        assertThat(key).isEqualTo("BLOCK_STREAM_INFO");
    }

    @Test
    @DisplayName("should always return default instance")
    void set() {
        // when
        final var blockStreamInfo = BlockStreamInfo.newBuilder()
                .blockNumber(0) // Setting to 0 to indicate a default or empty state
                .inputTreeRootHash(Bytes.wrap(domainBuilder.bytes(20)))
                .build();
        blockStreamInfoSingleton.set(blockStreamInfo);

        // then
        assertThat(blockStreamInfoSingleton.get()).isEqualTo(BlockStreamInfo.DEFAULT);
    }
}
