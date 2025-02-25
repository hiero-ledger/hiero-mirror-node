// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicMessageRepositoryTest extends ImporterIntegrationTest {

    private final TopicMessageRepository topicMessageRepository;

    @Test
    void prune() {
        domainBuilder.topicMessage().persist();
        var topicMessage2 = domainBuilder.topicMessage().persist();
        var topicMessage3 = domainBuilder.topicMessage().persist();

        topicMessageRepository.prune(topicMessage2.getConsensusTimestamp());

        assertThat(topicMessageRepository.findAll()).containsExactly(topicMessage3);
    }

    @Test
    void save() {
        var topicMessage = domainBuilder.topicMessage().get();
        topicMessageRepository.save(topicMessage);
        assertThat(topicMessageRepository.findById(topicMessage.getId())).get().isEqualTo(topicMessage);
    }
}
