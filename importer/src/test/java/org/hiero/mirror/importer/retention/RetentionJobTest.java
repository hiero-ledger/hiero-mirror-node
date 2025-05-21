// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class RetentionJobTest extends ImporterIntegrationTest {

    private final RecordFileRepository recordFileRepository;
    private final RetentionJob retentionJob;
    private final RetentionProperties retentionProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        retentionProperties.setBatchPeriod(Duration.ofSeconds(1L));
        retentionProperties.setExclude(Collections.emptySet());
        retentionProperties.setInclude(Collections.emptySet());
        retentionProperties.setPeriod(Duration.ofDays(-1L));
        retentionProperties.setEnabled(true);
    }

    @AfterEach
    void cleanup() {
        retentionProperties.setEnabled(false);
    }

    @Test
    void disabled() {
        // given
        retentionProperties.setEnabled(false);
        var recordFile = recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertThat(transactionRepository.count()).isEqualTo(1L);
    }

    @Test
    void noData() {
        // given
        var transaction = domainBuilder.transaction().persist();

        // when
        retentionJob.prune();

        // then
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
    }

    @Test
    void include() {
        // given
        retentionProperties.setInclude(Set.of("transaction"));
        var recordFile = recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void exclude() {
        // given
        retentionProperties.setExclude(Set.of("record_file"));
        var recordFile = recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void includeAndExclude() {
        // given
        retentionProperties.setInclude(Set.of("record_file", "transaction"));
        retentionProperties.setExclude(Set.of("record_file"));
        var recordFile = recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile);
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void pruneSingle() {
        // given
        recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void pruneNothing() {
        // given
        retentionProperties.setPeriod(Duration.ofDays(30));
        var recordFile1 = recordFile();
        var recordFile2 = recordFile();
        var recordFile3 = recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactlyInAnyOrder(recordFile1, recordFile2, recordFile3);
        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    @Test
    void prunePartial() {
        // given
        recordFile();
        var recordFile2 = recordFile();
        var recordFile3 = recordFile();
        var period = recordFile3.getConsensusEnd() - recordFile2.getConsensusEnd() - 1;
        retentionProperties.setPeriod(Duration.ofSeconds(0, period));

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.findAll()).containsExactly(recordFile3);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void pruneEverything() {
        // given
        recordFile();
        recordFile();
        recordFile();

        // when
        retentionJob.prune();

        // then
        assertThat(recordFileRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    private RecordFile recordFile() {
        var recordFile = domainBuilder.recordFile().persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordFile.getConsensusEnd()))
                .persist();
        return recordFile;
    }
}
