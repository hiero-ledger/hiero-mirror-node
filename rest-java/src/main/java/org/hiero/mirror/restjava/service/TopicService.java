// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.Topic;
import org.jspecify.annotations.NonNull;

public interface TopicService {

    Topic findById(@NonNull EntityId id);
}
