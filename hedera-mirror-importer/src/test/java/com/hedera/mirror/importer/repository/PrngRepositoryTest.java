// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class PrngRepositoryTest extends ImporterIntegrationTest {

    private final PrngRepository prngRepository;

    @Test
    void prune() {
        domainBuilder.prng().persist();
        var prng2 = domainBuilder.prng().persist();
        var prng3 = domainBuilder.prng().persist();

        prngRepository.prune(prng2.getId());

        assertThat(prngRepository.findAll()).containsExactly(prng3);
    }

    @Test
    void save() {
        var prng = domainBuilder.prng().get();

        prngRepository.save(prng);
        assertThat(prngRepository.findAll()).contains(prng);
    }
}
