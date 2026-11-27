// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class RegisteredNodeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final RegisteredNodeHistoryRepository registeredNodeHistoryRepository;

    @Test
    void prune() {
        domainBuilder.registeredNodeHistory().persist();
        final var registeredNodeHistory2 = domainBuilder.registeredNodeHistory().persist();
        final var registeredNodeHistory3 = domainBuilder.registeredNodeHistory().persist();

        registeredNodeHistoryRepository.prune(registeredNodeHistory2.getTimestampUpper());

        assertThat(registeredNodeHistoryRepository.findAll()).containsExactly(registeredNodeHistory3);
    }
}
