// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.Topic;
import jakarta.annotation.Nonnull;

public interface TopicService {

    Topic findById(@Nonnull EntityId id);
}
