// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.listener;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import jakarta.annotation.Resource;
import lombok.CustomLog;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;

@CustomLog
@SuppressWarnings("java:S2187") // Ignore no tests in file warning
class RedisTopicListenerTest extends AbstractSharedTopicListenerTest {

    @Resource
    private ReactiveRedisOperations<String, TopicMessage> redisOperations;

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
