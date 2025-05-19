// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionSignatureRepositoryTest extends Web3IntegrationTest {

    private final TransactionSignatureRepository transactionSignatureRepository;

    @Test
    void findByEntityIdSuccessful() {
        final var transactionSignature = domainBuilder.transactionSignature().persist();
        assertThat(transactionSignatureRepository.findByEntityId(transactionSignature.getEntityId()))
                .contains(transactionSignature);
    }

    @Test
    void findByEntityIdFailsWhenEntityIdDoesNotMatch() {
        final var transactionSignature = domainBuilder.transactionSignature().persist();
        final var wrongEntity = domainBuilder.entity().persist();
        assertThat(transactionSignatureRepository.findByEntityId(wrongEntity.toEntityId()))
                .doesNotContain(transactionSignature);
    }

    @Test
    void findByEntityIdWithTimestampSuccessful() {
        final long timestamp = Instant.now().getEpochSecond();
        final var transactionSignature = domainBuilder
                .transactionSignature()
                .customize(e -> e.consensusTimestamp(timestamp))
                .persist();
        assertThat(transactionSignatureRepository.findByEntityIdAndConsensusTimestampLessThanEqual(
                        transactionSignature.getEntityId(), timestamp))
                .contains(transactionSignature);
    }

    @Test
    void findByEntityIdWithTimestampFailsWhenTimestampIsBeforeTransaction() {
        final long timestamp = Instant.now().getEpochSecond();
        final var transactionSignature = domainBuilder
                .transactionSignature()
                .customize(e -> e.consensusTimestamp(timestamp))
                .persist();
        assertThat(transactionSignatureRepository.findByEntityIdAndConsensusTimestampLessThanEqual(
                        transactionSignature.getEntityId(), timestamp - 1))
                .doesNotContain(transactionSignature);
    }
}
