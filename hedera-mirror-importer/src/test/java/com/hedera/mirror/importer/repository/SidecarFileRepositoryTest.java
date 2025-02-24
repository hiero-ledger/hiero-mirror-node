// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SidecarFileRepositoryTest extends ImporterIntegrationTest {

    private final SidecarFileRepository sidecarFileRepository;

    @Test
    void prune() {
        domainBuilder.sidecarFile().persist();
        var sidecarFile2 = domainBuilder.sidecarFile().persist();
        var sidecarFile3 = domainBuilder.sidecarFile().persist();

        sidecarFileRepository.prune(sidecarFile2.getConsensusEnd());

        assertThat(sidecarFileRepository.findAll()).containsOnly(sidecarFile3);
    }

    @Test
    void save() {
        var sidecarFile1 = domainBuilder.sidecarFile().get();
        var sidecarFile2 = domainBuilder.sidecarFile().get();
        sidecarFileRepository.saveAll(List.of(sidecarFile1, sidecarFile2));
        assertThat(sidecarFileRepository.findAll()).containsExactlyInAnyOrder(sidecarFile1, sidecarFile2);
    }
}
