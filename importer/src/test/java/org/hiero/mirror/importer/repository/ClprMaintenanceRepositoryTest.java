// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprMaintenanceRepositoryTest extends ImporterIntegrationTest {

    private final ClprMaintenanceRepository clprMaintenanceRepository;

    @Test
    void prune() {
        domainBuilder.clprMaintenance().persist();
        var clprMaintenance2 = domainBuilder.clprMaintenance().persist();
        var clprMaintenance3 = domainBuilder.clprMaintenance().persist();

        clprMaintenanceRepository.prune(clprMaintenance2.getConsensusTimestamp());

        assertThat(clprMaintenanceRepository.findAll()).containsExactly(clprMaintenance3);
    }

    @Test
    void save() {
        var clprMaintenance = domainBuilder.clprMaintenance().persist();
        assertThat(clprMaintenanceRepository.findById(clprMaintenance.getConsensusTimestamp()))
                .get()
                .isEqualTo(clprMaintenance);
    }
}
