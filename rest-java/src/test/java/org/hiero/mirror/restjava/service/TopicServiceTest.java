// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TopicServiceTest extends RestJavaIntegrationTest {

    private final TopicService service;

    @Test
    void findById() {
        var topic = domainBuilder.topic().persist();
        assertThat(service.findById(EntityId.of(topic.getId()))).isEqualTo(topic);
    }

    @Test
    void findByIdNotFound() {
        var entityId = EntityId.of(9L);
        assertThatThrownBy(() -> service.findById(entityId)).isInstanceOf(EntityNotFoundException.class);
    }
}
