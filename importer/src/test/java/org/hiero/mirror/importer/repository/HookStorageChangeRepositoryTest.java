// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class HookStorageChangeRepositoryTest extends ImporterIntegrationTest {

    private final HookStorageChangeRepository hookStorageChangeRepository;

    @Test
    void prune() {
        domainBuilder.hookStorageChange().persist();
        var hookStorageChange2 = domainBuilder.hookStorageChange().persist();
        var hookStorageChange3 = domainBuilder.hookStorageChange().persist();

        hookStorageChangeRepository.prune(hookStorageChange2.getConsensusTimestamp());

        assertThat(hookStorageChangeRepository.findAll()).containsExactly(hookStorageChange3);
    }

    @Test
    void save() {
        var hookStorageChange = domainBuilder.hookStorageChange().persist();
        assertThat(hookStorageChangeRepository.findById(hookStorageChange.getId()))
                .get()
                .isEqualTo(hookStorageChange);
    }
}
