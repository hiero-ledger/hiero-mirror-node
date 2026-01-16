// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TopicHistoryRepository topicHistoryRepository;

    @Test
    void prune() {
        domainBuilder.topicHistory().persist();
        var topicHistory2 = domainBuilder.topicHistory().persist();
        var topicHistory3 = domainBuilder.topicHistory().persist();

        topicHistoryRepository.prune(topicHistory2.getTimestampUpper());

        assertThat(topicHistoryRepository.findAll()).containsExactly(topicHistory3);
    }
}
