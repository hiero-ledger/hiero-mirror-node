// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import io.micrometer.observation.ObservationRegistry;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@CustomLog
@Named
public class RedisTopicListener extends SharedTopicListener {

    private final Mono<ReactiveRedisMessageListenerContainer> container;
    private final SerializationPair<String> channelSerializer;
    private final SerializationPair<TopicMessage> messageSerializer;
    private final Map<String, Flux<TopicMessage>> topicMessages; // Topic name to active subscription

    public RedisTopicListener(
            ListenerProperties listenerProperties,
            ObservationRegistry observationRegistry,
            ReactiveRedisConnectionFactory connectionFactory,
            RedisSerializer<TopicMessage> redisSerializer) {
        super(listenerProperties);
        this.channelSerializer = SerializationPair.fromSerializer(RedisSerializer.string());
        this.messageSerializer = SerializationPair.fromSerializer(redisSerializer);
        this.topicMessages = new ConcurrentHashMap<>();

        // Workaround Spring DATAREDIS-1208 by lazily starting connection once with retry
        Duration interval = listenerProperties.getInterval();
        this.container = Mono.defer(() -> Mono.just(new ReactiveRedisMessageListenerContainer(connectionFactory)))
                .name(METRIC)
                .tag(METRIC_TAG, "redis")
                .tap(Micrometer.observation(observationRegistry))
                .doOnError(t -> log.error("Error connecting to Redis: ", t))
                .doOnSubscribe(s -> log.info("Attempting to connect to Redis"))
                .doOnSuccess(c -> log.info("Connected to Redis"))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(8)))
                .cache();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        Topic topic = getTopic(filter);
        return topicMessages.computeIfAbsent(topic.getTopic(), key -> subscribe(topic));
    }

    private Topic getTopic(TopicMessageFilter filter) {
        return ChannelTopic.of(String.format("topic.%d", filter.getTopicId().getId()));
    }

    private Flux<TopicMessage> subscribe(Topic topic) {
        Duration interval = listenerProperties.getInterval();

        return container
                .flatMapMany(r -> r.receive(Collections.singletonList(topic), channelSerializer, messageSerializer))
                .map(Message::getMessage)
                .doOnCancel(() -> unsubscribe(topic))
                .doOnComplete(() -> unsubscribe(topic))
                .doOnError(t -> log.error("Error listening for messages", t))
                .doOnSubscribe(s -> log.info("Creating shared subscription to {}", topic))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(4L)))
                .share();
    }

    private void unsubscribe(Topic topic) {
        topicMessages.remove(topic.getTopic());
        log.info("Unsubscribing from {}", topic);
    }
}
