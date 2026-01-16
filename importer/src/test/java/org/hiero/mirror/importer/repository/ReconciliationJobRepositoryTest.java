// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
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
        var reconciliationJob3 = domainBuilder.reconciliationJob().persist();

        assertThat(reconciliationJobRepository.findLatest()).get().isEqualTo(reconciliationJob3);
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
