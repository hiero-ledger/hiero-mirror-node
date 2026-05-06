// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class RecordFileTransformerTest {

    @Mock
    private BlockTransactionTransformerFactory factory;

    @Test
    void transform() {
        // given
        final var transformer = new BlockFileTransformer(factory);
        final var blockFile = BlockFile.builder().recordFile(new RecordFile()).build();

        // when
        final var actual = transformer.transform(blockFile);

        // then
        assertThat(actual).isSameAs(blockFile.getRecordFile());
        verifyNoInteractions(factory);
    }
}
