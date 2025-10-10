// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class HookRepositoryTest extends ImporterIntegrationTest {

    private final HookRepository hookRepository;

    @Test
    void save() {
        var hook = hookRepository.save(domainBuilder.hook().get());
        assertThat(hookRepository.findById(hook.getId())).get().isEqualTo(hook);
    }
}
