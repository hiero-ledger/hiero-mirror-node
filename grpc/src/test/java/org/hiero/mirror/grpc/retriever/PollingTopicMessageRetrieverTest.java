// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.retriever;

import static org.hiero.mirror.grpc.domain.ReactiveDomainBuilder.TOPIC_ID;

import java.time.Duration;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.domain.ReactiveDomainBuilder;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class PollingTopicMessageRetrieverTest extends GrpcIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    private final ReactiveDomainBuilder domainBuilder;
    private final PollingTopicMessageRetriever pollingTopicMessageRetriever;
    private final RetrieverProperties retrieverProperties;
    private final long now = DomainUtils.now();

    private long unthrottledMaxPolls;
    private Duration unthrottledPollingFrequency;

    @BeforeEach
    void setup() {
        unthrottledMaxPolls = retrieverProperties.getUnthrottled().getMaxPolls();
        retrieverProperties.getUnthrottled().setMaxPolls(2);

        unthrottledPollingFrequency = retrieverProperties.getUnthrottled().getPollingFrequency();
        retrieverProperties.getUnthrottled().setPollingFrequency(Duration.ofMillis(5L));
    }

    @AfterEach
    void teardown() {
        retrieverProperties.getUnthrottled().setMaxPolls(unthrottledMaxPolls);
        retrieverProperties.getUnthrottled().setPollingFrequency(unthrottledPollingFrequency);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notEnabled(boolean throttled) {
        retrieverProperties.setEnabled(false);
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttled).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);

        retrieverProperties.setEnabled(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noMessages(boolean throttled) {
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttled).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lessThanPageSize(boolean throttle) {
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L)
                .expectComplete()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void equalPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void limitEqualPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessages(4, now).blockLast();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(0)
                .limit(2L)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void greaterThanPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeBefore(boolean throttle) {
        domainBuilder.topicMessages(10, now).blockLast();
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeEquals(boolean throttle) {
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now)).block();
        var filter =
                TopicMessageFilter.builder().startTime(now).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeAfter(boolean throttle) {
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now - 1L)).block();
        var filter =
                TopicMessageFilter.builder().startTime(now).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle))
                .thenAwait(WAIT)
                .expectNextCount(0)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void topicId(boolean throttle) {
        domainBuilder.topicMessage(t -> t.topicId(EntityId.of(1L))).block();
        domainBuilder.topicMessage(t -> t.topicId(EntityId.of(2L))).block();
        domainBuilder.topicMessage(t -> t.topicId(EntityId.of(3L))).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(2L))
                .build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(2L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timeout(boolean throttle) {
        int maxPageSize = retrieverProperties.getMaxPageSize();
        Duration timeout = retrieverProperties.getTimeout();
        retrieverProperties.setMaxPageSize(1);
        retrieverProperties.setTimeout(Duration.ofMillis(10));

        domainBuilder.topicMessages(10, now).blockLast();
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .thenConsumeWhile(i -> true)
                .expectTimeout(Duration.ofMillis(500))
                .verify(WAIT);

        retrieverProperties.setMaxPageSize(maxPageSize);
        retrieverProperties.setTimeout(timeout);
    }

    @Test
    void unthrottledShouldKeepPolling() {
        retrieverProperties.getUnthrottled().setMaxPolls(20);
        Flux<TopicMessage> firstBatch = domainBuilder.topicMessages(5, now);
        Flux<TopicMessage> secondBatch = domainBuilder.topicMessages(5, now + 5);
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        // in unthrottled mode, the retriever should query the db for up to MaxPolls + 1 times when no limit is set,
        // regardless of whether a db query returns less rows than MaxPageSize
        StepVerifier.withVirtualTime(() ->
                        pollingTopicMessageRetriever.retrieve(filter, false).map(TopicMessage::getSequenceNumber))
                .then(firstBatch::blockLast)
                .then(secondBatch::blockLast)
                .thenAwait(WAIT)
                .expectNextSequence(LongStream.range(1, 11).boxed().toList())
                .expectComplete()
                .verify(WAIT);
    }

    int overrideMaxPageSize(boolean throttle, int newMaxPageSize) {
        int maxPageSize;

        if (throttle) {
            maxPageSize = retrieverProperties.getMaxPageSize();
            retrieverProperties.setMaxPageSize(newMaxPageSize);
        } else {
            maxPageSize = retrieverProperties.getUnthrottled().getMaxPageSize();
            retrieverProperties.getUnthrottled().setMaxPageSize(newMaxPageSize);
        }

        return maxPageSize;
    }

    void restoreMaxPageSize(boolean throttle, int maxPageSize) {
        if (throttle) {
            retrieverProperties.setMaxPageSize(maxPageSize);
        } else {
            retrieverProperties.getUnthrottled().setMaxPageSize(maxPageSize);
        }
    }
}
