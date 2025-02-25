// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.service;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

public interface TopicMessageService {

    Flux<TopicMessage> subscribeTopic(@Valid TopicMessageFilter filter);
}
