// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NetworkStakeRepositoryTest extends RestJavaIntegrationTest {

    private final NetworkStakeRepository networkStakeRepository;

    @Test
    void findLatest() {
        // given
        final var stake = domainBuilder.networkStake().persist();

        // when
        final var result = networkStakeRepository.findLatest();

        // then
        assertThat(result)
                .get()
                .usingRecursiveComparison()
                .ignoringFields("consensusTimestamp", "epochDay")
                .isEqualTo(stake);
    }

    @Test
    void findLatestMultipleEntries() {
        // given
        final var consensusTimestamp = Instant.now().toEpochMilli();
        domainBuilder
                .networkStake()
                .customize(stake -> stake.consensusTimestamp(consensusTimestamp))
                .persist();
        final var latest = domainBuilder
                .networkStake()
                .customize(stake -> stake.consensusTimestamp(consensusTimestamp + 1))
                .persist();

        // when
        final var result = networkStakeRepository.findLatest();

        // then
        assertThat(result)
                .get()
                .usingRecursiveComparison()
                .ignoringFields("consensusTimestamp", "epochDay")
                .isEqualTo(latest);
    }

    @Test
    void findLatestEmpty() {
        assertThat(networkStakeRepository.findLatest()).isEmpty();
    }
}
