// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

@Named
@Primary
@RequiredArgsConstructor
final class CompositeTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final MeterRegistry meterRegistry;
    private final PollingTopicListener pollingTopicListener;
    private final RedisTopicListener redisTopicListener;
    private final SharedPollingTopicListener sharedPollingTopicListener;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Timer consensusLatencyTimer = Timer.builder("hiero.mirror.grpc.consensus.latency")
            .description("The difference in ms between the time consensus was achieved and the message was sent")
            .tag("type", TopicMessage.class.getSimpleName())
            .register(meterRegistry);

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        if (!listenerProperties.isEnabled()) {
            return Flux.empty();
        }

        return getTopicListener()
                .listen(filter)
                .filter(t -> filterMessage(t, filter))
                .doOnNext(this::recordMetric);
    }

    private TopicListener getTopicListener() {
        final var type = listenerProperties.getType();

        switch (type) {
            case POLL:
                return pollingTopicListener;
            case REDIS:
                return redisTopicListener;
            case SHARED_POLL:
                return sharedPollingTopicListener;
            default:
                throw new UnsupportedOperationException("Unknown listener type: " + type);
        }
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return message.getTopicId().equals(filter.getTopicId())
                && message.getConsensusTimestamp() >= filter.getStartTime();
    }

    private void recordMetric(TopicMessage topicMessage) {
        long latency = System.currentTimeMillis() - (topicMessage.getConsensusTimestamp() / 1000000);
        getConsensusLatencyTimer().record(latency, TimeUnit.MILLISECONDS);
    }
}
