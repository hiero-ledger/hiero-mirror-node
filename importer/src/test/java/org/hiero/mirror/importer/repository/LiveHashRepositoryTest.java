// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class LiveHashRepositoryTest extends ImporterIntegrationTest {

    private final LiveHashRepository liveHashRepository;

    @Test
    void prune() {
        domainBuilder.liveHash().persist();
        var liveHash2 = domainBuilder.liveHash().persist();
        var liveHash3 = domainBuilder.liveHash().persist();

        liveHashRepository.prune(liveHash2.getConsensusTimestamp());

        assertThat(liveHashRepository.findAll()).containsExactly(liveHash3);
    }

    @Test
    void save() {
        var liveHash = domainBuilder.liveHash().get();
        liveHash = liveHashRepository.save(liveHash);

        assertThat(liveHashRepository.findById(liveHash.getId())).get().isEqualTo(liveHash);
    }
}
