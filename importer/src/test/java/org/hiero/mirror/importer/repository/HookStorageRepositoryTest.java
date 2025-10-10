// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class HookStorageRepositoryTest extends ImporterIntegrationTest {

    private final HookStorageRepository hookStorageRepository;

    @Test
    void save() {
        var hookStorage = hookStorageRepository.save(domainBuilder.hookStorage().persist());
        assertThat(hookStorageRepository.findById(hookStorage.getId())).get().isEqualTo(hookStorage);
    }
}
