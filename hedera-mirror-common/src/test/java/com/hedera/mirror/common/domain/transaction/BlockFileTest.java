// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlockFileTest {

    @Test
    void getBlockStreamFilename() {
        assertThat(BlockFile.getBlockStreamFilename(0)).isEqualTo("000000000000000000000000000000000000.blk.gz");
        assertThat(BlockFile.getBlockStreamFilename(1)).isEqualTo("000000000000000000000000000000000001.blk.gz");
        assertThatThrownBy(() -> BlockFile.getBlockStreamFilename(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block number must be non-negative");
    }

    @Test
    void onNewRound() {
        var blockFile = BlockFile.builder().onNewRound(1L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(1L, BlockFile::getRoundEnd);

        blockFile = BlockFile.builder().onNewRound(1L).onNewRound(2L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(2L, BlockFile::getRoundEnd);
    }
}
