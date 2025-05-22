// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class RecordFileRepositoryTest extends ImporterIntegrationTest {

    private final RecordFileRepository recordFileRepository;

    @Test
    void findFirst() {
        // empty
        assertThat(recordFileRepository.findFirst()).isEmpty();

        var first = domainBuilder.recordFile().persist();
        assertThat(recordFileRepository.findFirst()).get().isEqualTo(first);

        // first stays the same
        domainBuilder.recordFile().persist();
        assertThat(recordFileRepository.findFirst()).get().isEqualTo(first);
    }

    @Test
    void findLatest() {
        assertThat(recordFileRepository.findLatest()).isEmpty();

        domainBuilder.recordFile().persist();
        domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatest()).get().isEqualTo(recordFile3);
    }

    @Test
    void findLatestBefore() {
        assertThat(recordFileRepository.findLatestBefore(DomainUtils.now())).isEmpty();

        domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatestBefore(DomainUtils.now()))
                .get()
                .isEqualTo(recordFile2);

        var recordFile3 = domainBuilder.recordFile().persist();
        assertThat(recordFileRepository.findLatestBefore(DomainUtils.now()))
                .get()
                .isEqualTo(recordFile3);
    }

    @Test
    void findLatestWithOffset() {
        assertThat(recordFileRepository.findLatestWithOffset(0)).isEmpty();

        var recordFile1 = domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();

        var offset = recordFile3.getConsensusEnd() - recordFile2.getConsensusEnd();
        assertThat(recordFileRepository.findLatestWithOffset(offset)).get().isEqualTo(recordFile1);
        assertThat(recordFileRepository.findLatestWithOffset(0)).get().isEqualTo(recordFile2);
        assertThat(recordFileRepository.findLatestWithOffset(-1)).get().isEqualTo(recordFile3);
    }

    @Test
    void findLatestMissingGasUsedBefore() {
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(100L)).isEmpty();

        var rf1 = domainBuilder.recordFile().customize(r -> r.gasUsed(-1)).persist();
        var rf2 = domainBuilder.recordFile().persist();
        var rf3 = domainBuilder.recordFile().customize(r -> r.gasUsed(-1)).persist();
        var rf4 = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(rf4.getConsensusEnd() + 1L))
                .get()
                .isEqualTo(rf3);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(rf3.getConsensusEnd()))
                .get()
                .isEqualTo(rf1);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(rf2.getConsensusEnd()))
                .get()
                .isEqualTo(rf1);
        assertThat(recordFileRepository.findLatestMissingGasUsedBefore(rf1.getConsensusEnd()))
                .isEmpty();
    }

    @Test
    void findNextBetween() {
        var rf1 = domainBuilder.recordFile().persist();
        var rf2 = domainBuilder.recordFile().persist();
        var rf3 = domainBuilder.recordFile().persist();
        var max = Long.MAX_VALUE;

        assertThat(recordFileRepository.findNextBetween(0, max)).get().isEqualTo(rf1);
        assertThat(recordFileRepository.findNextBetween(rf1.getConsensusEnd(), max))
                .get()
                .isEqualTo(rf2);
        assertThat(recordFileRepository.findNextBetween(rf2.getConsensusEnd(), max))
                .get()
                .isEqualTo(rf3);
        assertThat(recordFileRepository.findNextBetween(rf3.getConsensusEnd(), max))
                .isEmpty();

        max = rf3.getConsensusEnd();
        assertThat(recordFileRepository.findNextBetween(0, max)).get().isEqualTo(rf1);
        assertThat(recordFileRepository.findNextBetween(rf1.getConsensusEnd(), max))
                .get()
                .isEqualTo(rf2);
        assertThat(recordFileRepository.findNextBetween(rf2.getConsensusEnd(), max))
                .get()
                .isEqualTo(rf3);
        assertThat(recordFileRepository.findNextBetween(rf3.getConsensusEnd(), max))
                .isEmpty();
    }

    @Test
    void prune() {
        domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();
        var recordFile3 = domainBuilder.recordFile().persist();

        recordFileRepository.prune(recordFile2.getConsensusEnd());

        assertThat(recordFileRepository.findAll()).containsExactly(recordFile3);
    }

    @Test
    void updateIndex() {
        var recordFile1 = domainBuilder.recordFile().persist();
        var recordFile2 = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.updateIndex(-2L)).isEqualTo(2);
        assertThat(recordFileRepository.findById(recordFile1.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getIndex)
                .isEqualTo(recordFile1.getIndex() - 2);

        assertThat(recordFileRepository.findById(recordFile2.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getIndex)
                .isEqualTo(recordFile2.getIndex() - 2);
    }
}
