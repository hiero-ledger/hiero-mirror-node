// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NftHistoryRepositoryTest extends ImporterIntegrationTest {

    private final NftHistoryRepository repository;

    @Test
    void prune() {
        // given
        var nftHistory1 = domainBuilder.nftHistory().persist();
        var nftHistory2 = domainBuilder
                .nftHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(nftHistory1.getTimestampUpper(), nftHistory1.getTimestampUpper() + 5)))
                .persist();
        var nftHistory3 = domainBuilder
                .nftHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(nftHistory2.getTimestampUpper(), nftHistory2.getTimestampUpper() + 5)))
                .persist();

        // when
        repository.prune(nftHistory2.getTimestampLower());

        // then
        assertThat(repository.findAll()).containsExactlyInAnyOrder(nftHistory2, nftHistory3);

        // when
        repository.prune(nftHistory3.getTimestampLower() + 1);

        // then
        assertThat(repository.findAll()).containsExactly(nftHistory3);
    }

    @Test
    void save() {
        var nftHistory = domainBuilder.nftHistory().get();
        repository.save(nftHistory);
        assertThat(repository.findAll()).containsExactly(nftHistory);
        assertThat(repository.findById(nftHistory.getId())).get().isEqualTo(nftHistory);
    }
}
