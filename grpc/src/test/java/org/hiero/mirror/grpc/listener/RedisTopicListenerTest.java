// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;

@CustomLog
@RequiredArgsConstructor
@SuppressWarnings("java:S2187") // Ignore no tests in file warning
final class RedisTopicListenerTest extends AbstractSharedTopicListenerTest {

    private final ReactiveRedisOperations<String, TopicMessage> redisOperations;

    @Override
    protected ListenerProperties.ListenerType getType() {
        return ListenerProperties.ListenerType.REDIS;
    }

    @Override
    protected void publish(Flux<TopicMessage> publisher) {
        publisher.concatMap(t -> redisOperations.convertAndSend(getTopic(t), t)).blockLast();
    }

    private String getTopic(TopicMessage topicMessage) {
        return "topic." + topicMessage.getTopicId().getId();
    }
}
