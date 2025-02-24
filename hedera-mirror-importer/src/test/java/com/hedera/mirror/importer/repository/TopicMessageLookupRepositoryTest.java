// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicMessageLookupRepositoryTest extends ImporterIntegrationTest {

    private final TopicMessageLookupRepository repository;

    @Test
    void prune() {
        domainBuilder.topicMessageLookup().persist();
        var topicMessageLookup2 = domainBuilder.topicMessageLookup().persist();
        var topicMessageLookup3 = domainBuilder.topicMessageLookup().persist();
        repository.prune(topicMessageLookup2.getTimestampRange().upperEndpoint());
        assertThat(repository.findAll()).containsOnly(topicMessageLookup3);
    }

    @Test
    void save() {
        var topicMessageLookup = domainBuilder.topicMessageLookup().get();
        repository.save(topicMessageLookup);
        assertThat(repository.findById(topicMessageLookup.getId())).get().isEqualTo(topicMessageLookup);
    }
}
