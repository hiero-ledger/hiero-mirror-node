// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprLedgerConfigurationRepositoryTest extends ImporterIntegrationTest {

    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;

    @Test
    void save() {
        var clprLedgerConfiguration = domainBuilder.clprLedgerConfiguration().persist();
        assertThat(clprLedgerConfigurationRepository.findById(clprLedgerConfiguration.getConsensusTimestamp()))
                .get()
                .isEqualTo(clprLedgerConfiguration);
    }

    @Test
    void findLatest() {
        domainBuilder.clprLedgerConfiguration().persist();
        var latest = domainBuilder.clprLedgerConfiguration().persist();
        assertThat(clprLedgerConfigurationRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findLatestEmpty() {
        assertThat(clprLedgerConfigurationRepository.findLatest()).isEmpty();
    }
}
