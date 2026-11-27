// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ReconciliationJobRepositoryTest extends ImporterIntegrationTest {

    private final ReconciliationJobRepository reconciliationJobRepository;

    @Test
    void findLatest() {
        assertThat(reconciliationJobRepository.findLatest()).isEmpty();

        domainBuilder.reconciliationJob().persist();
        domainBuilder.reconciliationJob().persist();
        var reconciliationJob3 = domainBuilder
                .reconciliationJob()
                .customize(r -> r.count(3L).error("error").status(ReconciliationStatus.FAILURE_TOKEN_TRANSFERS))
                .persist();

        assertThat(reconciliationJobRepository.findLatest()).get().isEqualTo(reconciliationJob3);
    }

    @Test
    void save() {
        // given a new job, saved the way the reconciliation service starts one
        final var reconciliationJob = domainBuilder
                .reconciliationJob()
                .customize(r -> r.count(5L)
                        .error("first error")
                        .status(ReconciliationStatus.RUNNING)
                        .timestampEnd(null))
                .get();
        reconciliationJobRepository.save(reconciliationJob);
        assertThat(reconciliationJobRepository.findAll()).containsExactly(reconciliationJob);

        // when the same job is updated, the way the service finishes one
        reconciliationJob.setConsensusTimestamp(domainBuilder.timestamp());
        reconciliationJob.setCount(10L);
        reconciliationJob.setError("second error");
        reconciliationJob.setStatus(ReconciliationStatus.FAILURE_CRYPTO_TRANSFERS);
        reconciliationJob.setTimestampEnd(reconciliationJob.getTimestampStart().plusSeconds(60));
        reconciliationJobRepository.save(reconciliationJob);

        // then the existing row is updated in place, with every column written
        assertThat(reconciliationJobRepository.findAll()).containsExactly(reconciliationJob);
    }

    @Test
    void prune() {
        domainBuilder.reconciliationJob().persist();
        var reconciliationJob2 = domainBuilder.reconciliationJob().persist();
        var reconciliationJob3 = domainBuilder.reconciliationJob().persist();

        reconciliationJobRepository.prune(reconciliationJob2.getConsensusTimestamp());

        assertThat(reconciliationJobRepository.findAll()).containsExactly(reconciliationJob3);
    }
}
