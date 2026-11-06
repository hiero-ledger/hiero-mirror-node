// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprChannelRepositoryTest extends ImporterIntegrationTest {

    private final ClprChannelRepository clprChannelRepository;

    @Test
    void save() {
        var clprChannel = domainBuilder.clprChannel().persist();
        assertThat(clprChannelRepository.findById(clprChannel.getChannelId()))
                .get()
                .isEqualTo(clprChannel);
    }
}
