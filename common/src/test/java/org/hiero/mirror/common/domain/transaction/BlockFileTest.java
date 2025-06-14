// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlockFileTest {

    @ParameterizedTest(name = "gzipped={0}")
    @CsvSource(value = {"true, '.gz'", "false, ''"})
    void getBlockStreamFilename(boolean gzipped, String extraSuffix) {
        assertThat(BlockFile.getFilename(0, gzipped))
                .isEqualTo("000000000000000000000000000000000000.blk" + extraSuffix);
        assertThat(BlockFile.getFilename(1, gzipped))
                .isEqualTo("000000000000000000000000000000000001.blk" + extraSuffix);
        assertThat(BlockFile.getFilename(0, gzipped))
                .isEqualTo("000000000000000000000000000000000000.blk" + extraSuffix);
        assertThat(BlockFile.getFilename(1, gzipped))
                .isEqualTo("000000000000000000000000000000000001.blk" + extraSuffix);
        assertThatThrownBy(() -> BlockFile.getFilename(-1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block number must be non-negative");
    }

    @Test
    void isStreamedFilename() {
        assertThat(BlockFile.isStreamedFilename("000000000000000000000000000000000000.blk"))
                .isTrue();
        assertThat(BlockFile.isStreamedFilename("000000000000000000000000000000000000.blk.gz"))
                .isFalse();
        assertThat(BlockFile.isStreamedFilename("2022-07-13T08_46_08.041986003Z.rcd.gz"))
                .isFalse();
    }

    @Test
    void onNewRound() {
        var blockFile = BlockFile.builder().onNewRound(1L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(1L, BlockFile::getRoundEnd);

        blockFile = BlockFile.builder().onNewRound(1L).onNewRound(2L).build();
        assertThat(blockFile).returns(1L, BlockFile::getRoundStart).returns(2L, BlockFile::getRoundEnd);
    }
}
