// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.GrpcProperties;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.hiero.mirror.grpc.listener.TopicListener;
import org.hiero.mirror.grpc.repository.EntityRepository;
import org.hiero.mirror.grpc.retriever.TopicMessageRetriever;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.retry.Repeat;

@Named
@CustomLog
@RequiredArgsConstructor
@Validated
public class TopicMessageServiceImpl implements TopicMessageService {

    private final GrpcProperties grpcProperties;
    private final TopicListener topicListener;
    private final EntityRepository entityRepository;
    private final TopicMessageRetriever topicMessageRetriever;
    private final MeterRegistry meterRegistry;
    private final AtomicLong subscriberCount = new AtomicLong(0L);

    @PostConstruct
    void init() {
        Gauge.builder("hiero.mirror.grpc.subscribers", () -> subscriberCount)
                .description("The number of active subscribers")
                .tag("type", TopicMessage.class.getSimpleName())
                .register(meterRegistry);
    }

    @Override
    public Flux<TopicMessage> subscribeTopic(TopicMessageFilter filter) {
        log.info("Subscribing to topic: {}", filter);
        TopicContext topicContext = new TopicContext(filter);

        Flux<TopicMessage> flux = topicMessageRetriever
                .retrieve(filter, true)
                .concatWith(Flux.defer(() -> incomingMessages(topicContext))) // Defer creation until query complete
                .filter(t -> t.compareTo(topicContext.getLast()) > 0); // Ignore duplicates

        if (filter.getEndTime() != null) {
            flux = flux.takeWhile(t -> t.getConsensusTimestamp() < filter.getEndTime());
        }

        if (filter.hasLimit()) {
            flux = flux.take(filter.getLimit());
        }

        return topicExists(filter)
                .thenMany(flux.doOnNext(topicContext::onNext)
                        .doOnSubscribe(s -> subscriberCount.incrementAndGet())
                        .doFinally(s -> subscriberCount.decrementAndGet())
                        .doFinally(topicContext::finished));
    }

    private Mono<?> topicExists(TopicMessageFilter filter) {
        var topicId = filter.getTopicId();
        return Mono.justOrEmpty(entityRepository.findById(topicId.getId()))
                .switchIfEmpty(
                        grpcProperties.isCheckTopicExists()
                                ? Mono.error(new EntityNotFoundException(topicId))
                                : Mono.just(Entity.builder()
                                        .memo("")
                                        .type(EntityType.TOPIC)
                                        .build()))
                .filter(e -> e.getType() == EntityType.TOPIC)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Not a valid topic")));
    }

    private Flux<TopicMessage> incomingMessages(TopicContext topicContext) {
        if (topicContext.isComplete()) {
            return Flux.empty();
        }

        TopicMessageFilter filter = topicContext.getFilter();
        TopicMessage last = topicContext.getLast();
        long limit =
                filter.hasLimit() ? filter.getLimit() - topicContext.getCount().get() : 0;
        long startTime = last != null ? last.getConsensusTimestamp() + 1 : filter.getStartTime();
        var newFilter = filter.toBuilder().limit(limit).startTime(startTime).build();

        return topicListener
                .listen(newFilter)
                .takeUntilOther(pastEndTime(topicContext))
                .concatMap(t -> missingMessages(topicContext, t));
    }

    private Flux<Object> pastEndTime(TopicContext topicContext) {
        if (topicContext.getFilter().getEndTime() == null) {
            return Flux.never();
        }

        return Flux.empty()
                .repeatWhen(Repeat.create(r -> !topicContext.isComplete(), Long.MAX_VALUE)
                        .fixedBackoff(grpcProperties.getEndTimeInterval()));
    }

    /**
     * A flow can have missing messages if the importer is down for a long time when the client subscribes. When the
     * incoming flow catches up and receives the next message for the topic, it will fill in any missing messages from
     * when it was down.
     */
    private Flux<TopicMessage> missingMessages(TopicContext topicContext, TopicMessage current) {
        if (topicContext.isNext(current)) {
            return Flux.just(current);
        }

        TopicMessage last = topicContext.getLast();
        long numMissingMessages = current.getSequenceNumber() - last.getSequenceNumber() - 1;

        // fail fast on out of order messages
        if (numMissingMessages < -1) {
            throw new IllegalStateException(
                    String.format("Encountered out of order missing messages, last: %s, current: %s", last, current));
        }

        // ignore duplicate message already processed by larger subscribe context
        if (numMissingMessages == -1) {
            log.debug("Encountered duplicate missing message to be ignored, last: {}, current: {}", last, current);
            return Flux.empty();
        }

        TopicMessageFilter newFilter = topicContext.getFilter().toBuilder()
                .endTime(current.getConsensusTimestamp())
                .limit(numMissingMessages)
                .startTime(last.getConsensusTimestamp() + 1)
                .build();

        log.info(
                "[{}] Querying topic {} for missing messages between sequence {} and {}",
                newFilter.getSubscriberId(),
                topicContext.getTopicId(),
                last.getSequenceNumber(),
                current.getSequenceNumber());

        return topicMessageRetriever.retrieve(newFilter, false).concatWithValues(current);
    }

    @Data
    private class TopicContext {

        private final AtomicLong count;
        private final TopicMessageFilter filter;
        private final AtomicReference<TopicMessage> last;
        private final long startTime;
        private final Stopwatch stopwatch;
        private final EntityId topicId;

        private TopicContext(TopicMessageFilter filter) {
            this.count = new AtomicLong(0L);
            this.filter = filter;
            this.last = new AtomicReference<>();
            this.startTime = DomainUtils.now();
            this.stopwatch = Stopwatch.createStarted();
            this.topicId = filter.getTopicId();
        }

        private TopicMessage getLast() {
            return last.get();
        }

        boolean isComplete() {
            if (filter.getEndTime() == null) {
                return false;
            }

            if (filter.getEndTime() < startTime) {
                return true;
            }

            return Instant.ofEpochSecond(0, filter.getEndTime())
                    .plus(grpcProperties.getEndTimeInterval())
                    .isBefore(Instant.now());
        }

        boolean isNext(TopicMessage topicMessage) {
            return getLast() == null
                    || topicMessage.getSequenceNumber() == getLast().getSequenceNumber() + 1;
        }

        private int rate() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            return elapsed > 0 ? (int) (1000.0 * count.get() / elapsed) : 0;
        }

        void finished(SignalType signalType) {
            log.info(
                    "[{}] Topic {} {} with {} messages in {} ({}/s)",
                    filter.getSubscriberId(),
                    signalType,
                    topicId,
                    count,
                    stopwatch,
                    rate());
        }

        void onNext(TopicMessage topicMessage) {
            if (!isNext(topicMessage)) {
                throw new IllegalStateException(
                        String.format("Encountered out of order messages, last: %s, current: %s", last, topicMessage));
            }

            last.set(topicMessage);
            count.incrementAndGet();
            if (log.isTraceEnabled()) {
                log.trace(
                        "[{}] Topic {} received message #{}: {}",
                        filter.getSubscriberId(),
                        topicId,
                        count,
                        topicMessage);
            }
        }
    }
}
