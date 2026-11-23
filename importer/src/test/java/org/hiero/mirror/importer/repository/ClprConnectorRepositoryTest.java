// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprConnectorRepositoryTest extends ImporterIntegrationTest {

    private final ClprConnectorRepository clprConnectorRepository;

    @Test
    void save() {
        var clprConnector = domainBuilder.clprConnector().persist();
        assertThat(clprConnectorRepository.findById(clprConnector.getId()))
                .get()
                .isEqualTo(clprConnector);
    }
}
