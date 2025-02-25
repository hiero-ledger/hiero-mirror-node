// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.repository;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

public interface TopicMessageRepositoryCustom {

    @Transactional(readOnly = true)
    Stream<TopicMessage> findByFilter(TopicMessageFilter filter);
}
