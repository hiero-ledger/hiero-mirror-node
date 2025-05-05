// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.listener.ListenerProperties.ListenerType;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

@Named
@CustomLog
@Primary
@RequiredArgsConstructor
public class CompositeTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final NotifyingTopicListener notifyingTopicListener;
    private final PollingTopicListener pollingTopicListener;
    private final RedisTopicListener redisTopicListener;
    private final SharedPollingTopicListener sharedPollingTopicListener;
    private final MeterRegistry meterRegistry;
    private Timer consensusLatencyTimer;

    @PostConstruct
    public void registerMetrics() {
        consensusLatencyTimer = Timer.builder("hiero.mirror.grpc.consensus.latency")
                .description("The difference in ms between the time consensus was achieved and the message was sent")
                .tag("type", TopicMessage.class.getSimpleName())
                .register(meterRegistry);
    }

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
        ListenerType type = listenerProperties.getType();

        switch (type) {
            case NOTIFY:
                return notifyingTopicListener;
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
        consensusLatencyTimer.record(latency, TimeUnit.MILLISECONDS);
    }
}
