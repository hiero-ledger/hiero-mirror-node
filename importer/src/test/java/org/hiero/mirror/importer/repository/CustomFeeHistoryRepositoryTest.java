// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final CustomFeeHistoryRepository repository;

    @Test
    void prune() {
        // given
        var history1 = domainBuilder.customFeeHistory().persist();
        var history2 = domainBuilder
                .customFeeHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(history1.getTimestampUpper(), history1.getTimestampUpper() + 5)))
                .persist();
        var history3 = domainBuilder
                .customFeeHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(history2.getTimestampUpper(), history2.getTimestampUpper() + 5)))
                .persist();

        // when
        repository.prune(history2.getTimestampLower());

        // then
        assertThat(repository.findAll()).containsExactlyInAnyOrder(history2, history3);

        // when
        repository.prune(history3.getTimestampLower() + 1);

        // then
        assertThat(repository.findAll()).containsExactly(history3);
    }

    @Test
    void save() {
        var customFeeHistory = domainBuilder.customFeeHistory().get();
        repository.save(customFeeHistory);
        assertThat(repository.findAll()).containsExactly(customFeeHistory);
        assertThat(repository.findById(customFeeHistory.getEntityId())).get().isEqualTo(customFeeHistory);
    }
}
