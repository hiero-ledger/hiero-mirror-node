// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicRepositoryTest extends ImporterIntegrationTest {

    private final TopicRepository topicRepository;

    @Test
    void save() {
        var topic = domainBuilder.topic().get();
        topicRepository.save(topic);
        assertThat(topicRepository.findById(topic.getId())).contains(topic);
    }
}
