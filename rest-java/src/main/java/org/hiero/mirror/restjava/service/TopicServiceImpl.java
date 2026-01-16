// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.restjava.repository.TopicRepository;

@Named
@RequiredArgsConstructor
final class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;

    @Override
    public Topic findById(EntityId id) {
        return topicRepository.findById(id.getId()).orElseThrow(() -> new EntityNotFoundException("Topic not found"));
    }
}
