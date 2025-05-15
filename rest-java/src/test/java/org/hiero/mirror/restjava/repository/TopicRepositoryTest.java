// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicRepositoryTest extends RestJavaIntegrationTest {

    private final TopicRepository topicRepository;

    @Test
    void findById() {
        var topic = domainBuilder.topic().persist();
        assertThat(topicRepository.findById(topic.getId())).contains(topic);
    }
}
