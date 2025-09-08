// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.junit.jupiter.api.Test;

final class UtilsTest {

    @Test
    void getLatency() {
        // given
        long consensusEnd = DomainUtils.convertToNanosMax(Instant.now());
        var blockFile = BlockFile.builder().consensusEnd(consensusEnd).build();
        long expectedDelay = 1000;
        long completeTime = consensusEnd / 1_000_000 + expectedDelay;
        var blockStream = new BlockStream(
                Collections.emptyList(),
                completeTime,
                null,
                BlockFile.getFilename(0, false),
                System.currentTimeMillis(),
                -1);

        // when, then
        assertThat(Utils.getLatency(blockFile, blockStream)).isEqualTo(expectedDelay);
    }
}
