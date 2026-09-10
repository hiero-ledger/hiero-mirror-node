// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenTransferRepositoryTest extends ImporterIntegrationTest {

    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void findByConsensusTimestamp() {
        var tokenTransfer1 = domainBuilder.tokenTransfer().persist();
        var tokenTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(t -> {
                    var existingId = tokenTransfer1.getId();
                    var id = new TokenTransfer.Id(
                            existingId.getConsensusTimestamp(), existingId.getTokenId(), domainBuilder.entityId());
                    t.id(id);
                })
                .persist();
        var tokenTransfer3 = domainBuilder.tokenTransfer().persist();

        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer1.getId().getConsensusTimestamp()))
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2);
        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer3.getId().getConsensusTimestamp()))
                .containsExactly(tokenTransfer3);
        assertThat(tokenTransferRepository.findByConsensusTimestamp(
                        tokenTransfer3.getId().getConsensusTimestamp() + 1))
                .isEmpty();
    }

    @Test
    void prune() {
        domainBuilder.tokenTransfer().persist();
        var tokenTransfer2 = domainBuilder.tokenTransfer().persist();
        var tokenTransfer3 = domainBuilder.tokenTransfer().persist();

        tokenTransferRepository.prune(tokenTransfer2.getId().getConsensusTimestamp());

        assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer3);
    }

    @Test
    void save() {
        var tokenTransfer = domainBuilder.tokenTransfer().get();
        tokenTransferRepository.save(tokenTransfer);
        assertThat(tokenTransferRepository.findById(tokenTransfer.getId()))
                .get()
                .isEqualTo(tokenTransfer);
    }
}
