// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
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
