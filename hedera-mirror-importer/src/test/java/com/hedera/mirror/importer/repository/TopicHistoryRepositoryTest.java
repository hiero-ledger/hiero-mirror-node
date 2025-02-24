// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
