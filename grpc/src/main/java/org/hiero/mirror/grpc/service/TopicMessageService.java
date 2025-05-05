// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import jakarta.validation.Valid;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import reactor.core.publisher.Flux;

public interface TopicMessageService {

    Flux<TopicMessage> subscribeTopic(@Valid TopicMessageFilter filter);
}
