// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprMessageRepositoryTest extends ImporterIntegrationTest {

    private final ClprMessageRepository clprMessageRepository;

    @Test
    void prune() {
        domainBuilder.clprMessage().persist();
        var clprMessage2 = domainBuilder.clprMessage().persist();
        var clprMessage3 = domainBuilder.clprMessage().persist();

        clprMessageRepository.prune(clprMessage2.getConsensusTimestamp());

        assertThat(clprMessageRepository.findAll()).containsExactly(clprMessage3);
    }

    @Test
    void save() {
        var clprMessage = domainBuilder.clprMessage().persist();
        assertThat(clprMessageRepository.findById(clprMessage.getId())).get().isEqualTo(clprMessage);
    }

    @Test
    void saveControlMessage() {
        var clprMessage = domainBuilder.clprControlMessage().persist();
        assertThat(clprMessageRepository.findById(clprMessage.getId())).get().isEqualTo(clprMessage);
    }

    @Test
    void saveResponseMessage() {
        var clprMessage = domainBuilder.clprResponseMessage().persist();
        assertThat(clprMessageRepository.findById(clprMessage.getId())).get().isEqualTo(clprMessage);
    }
}
