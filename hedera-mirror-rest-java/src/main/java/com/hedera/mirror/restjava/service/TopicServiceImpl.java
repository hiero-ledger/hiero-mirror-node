// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.Topic;
import com.hedera.mirror.restjava.repository.TopicRepository;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;

    @Override
    public Topic findById(@Nonnull EntityId id) {
        return topicRepository
                .findById(id.getId())
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + id));
    }
}
