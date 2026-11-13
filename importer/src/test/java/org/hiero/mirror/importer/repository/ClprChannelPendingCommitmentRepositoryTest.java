// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprChannelPendingCommitmentRepositoryTest extends ImporterIntegrationTest {

    private final ClprChannelPendingCommitmentRepository clprChannelPendingCommitmentRepository;

    @Test
    void save() {
        var clprChannelPendingCommitment =
                domainBuilder.clprChannelPendingCommitment().persist();
        assertThat(clprChannelPendingCommitmentRepository.findById(
                        clprChannelPendingCommitment.getOwnershipCommitment()))
                .get()
                .isEqualTo(clprChannelPendingCommitment);
    }
}
