// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import java.util.stream.Stream;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.transaction.annotation.Transactional;

public interface TopicMessageRepositoryCustom {

    @Transactional(readOnly = true)
    Stream<TopicMessage> findByFilter(TopicMessageFilter filter);
}
