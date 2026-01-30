// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import com.google.common.base.Stopwatch;
import io.micrometer.observation.ObservationRegistry;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.repository.TopicMessageRepository;
import org.reactivestreams.Subscription;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Repeat;
import reactor.util.retry.Retry;

@Named
public class SharedPollingTopicListener extends SharedTopicListener {

    private final TopicMessageRepository topicMessageRepository;
    private final Flux<TopicMessage> topicMessages;

    public SharedPollingTopicListener(
            ListenerProperties listenerProperties,
            ObservationRegistry observationRegistry,
            TopicMessageRepository topicMessageRepository) {
        super(listenerProperties);
        this.topicMessageRepository = topicMessageRepository;

        Scheduler scheduler = Schedulers.boundedElastic();
        Duration interval = listenerProperties.getInterval();
        PollingContext context = new PollingContext();

        topicMessages = Flux.defer(() -> poll(context).subscribeOn(scheduler))
                .repeatWhen(Repeat.times(Long.MAX_VALUE).fixedBackoff(interval).withBackoffScheduler(scheduler))
                .name(METRIC)
                .tag(METRIC_TAG, "shared poll")
                .tap(Micrometer.observation(observationRegistry))
                .doOnCancel(() -> log.info("Cancelled polling"))
                .doOnError(t -> log.error("Error polling the database", t))
                .doOnSubscribe(context::onStart)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(4L)))
                .share();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        return topicMessages;
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        if (!listenerProperties.isEnabled()) {
            return Flux.empty();
        }

        return Flux.fromIterable(topicMessageRepository.findLatest(
                        context.getLastConsensusTimestamp().get(), listenerProperties.getMaxPageSize()))
                .doOnNext(context::onNext)
                .doOnCancel(context::onPollEnd)
                .doOnComplete(context::onPollEnd)
                .doOnSubscribe(context::onPollStart);
    }

    @Data
    private class PollingContext {

        private final AtomicLong count = new AtomicLong(0L);
        private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private final AtomicLong lastConsensusTimestamp = new AtomicLong();

        void onNext(TopicMessage topicMessage) {
            count.incrementAndGet();
            lastConsensusTimestamp.set(topicMessage.getConsensusTimestamp());
            if (log.isTraceEnabled()) {
                log.trace("Next message: {}", topicMessage);
            }
        }

        void onPollEnd() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            var rate = elapsed > 0 ? (int) (1000.0 * count.get() / elapsed) : 0;
            log.info("Finished querying with {} messages in {} ({}/s)", count, stopwatch, rate);
            count.set(0L);
        }

        void onPollStart(Subscription subscription) {
            count.set(0L);
            stopwatch.reset().start();
            log.debug("Querying for messages after timestamp {}", lastConsensusTimestamp);
        }

        void onStart(Subscription subscription) {
            lastConsensusTimestamp.set(DomainUtils.now());
            log.info(
                    "Starting to poll every {}ms",
                    listenerProperties.getInterval().toMillis());
        }
    }
}
