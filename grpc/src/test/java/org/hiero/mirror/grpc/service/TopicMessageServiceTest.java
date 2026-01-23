// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.grpc.domain.ReactiveDomainBuilder.TOPIC_ID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.hiero.mirror.grpc.GrpcProperties;
import org.hiero.mirror.grpc.domain.ReactiveDomainBuilder;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.exception.EntityNotFoundException;
import org.hiero.mirror.grpc.listener.ListenerProperties;
import org.hiero.mirror.grpc.listener.TopicListener;
import org.hiero.mirror.grpc.repository.EntityRepository;
import org.hiero.mirror.grpc.retriever.RetrieverProperties;
import org.hiero.mirror.grpc.retriever.TopicMessageRetriever;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@NullUnmarked
@RequiredArgsConstructor
class TopicMessageServiceTest extends GrpcIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    private final long now = DomainUtils.now();
    private final long future = now + 30L * NANOS_PER_SECOND;

    private final ReactiveDomainBuilder domainBuilder;
    private final GrpcProperties grpcProperties;
    private final ListenerProperties listenerProperties;
    private final RetrieverProperties retrieverProperties;

    @Resource
    private TopicMessageService topicMessageService;

    @BeforeEach
    void setup() {
        listenerProperties.setEnabled(true);
        domainBuilder.entity().block();
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
    }

    @Test
    void invalidFilter() {
        var filter = TopicMessageFilter.builder()
                .startTime(-1)
                .topicId(null)
                .limit(-1)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .hasMessageContaining("startTime: must be greater than or equal to 0")
                .hasMessageContaining("topicId: must not be null");
    }

    @Test
    void endTimeBeforeStartTime() {
        var filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(now - 86400 * NANOS_PER_SECOND)
                .topicId(TOPIC_ID)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void endTimeEqualsStartTime() {
        var filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(now)
                .topicId(TOPIC_ID)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void startTimeAfterNow() {
        var filter = TopicMessageFilter.builder()
                .startTime(now + 60 * 60 * NANOS_PER_SECOND)
                .topicId(TOPIC_ID)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Start time must be before the current time");
    }

    @Test
    void topicNotFound() {
        var filter = TopicMessageFilter.builder().topicId(EntityId.of(999L)).build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectError(EntityNotFoundException.class)
                .verify(WAIT);
    }

    @Test
    void topicNotFoundWithCheckTopicExistsFalse() {
        grpcProperties.setCheckTopicExists(false);
        var filter = TopicMessageFilter.builder().topicId(EntityId.of(999L)).build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .expectSubscription()
                .expectNoEvent(WAIT)
                .thenCancel()
                .verify(WAIT);

        grpcProperties.setCheckTopicExists(true);
    }

    @Test
    void invalidTopic() {
        domainBuilder.entity(e -> e.type(EntityType.ACCOUNT)).block();
        var filter = TopicMessageFilter.builder().topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofMillis(100));
    }

    @Test
    void noMessages() {
        var filter = TopicMessageFilter.builder().topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void noMessagesWithPastEndTime() {
        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(1L)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void noMessagesWithFutureEndTime() {
        long endTime = now + 250_000_000L;
        var filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(endTime)
                .topicId(TOPIC_ID)
                .build();

        topicMessageService
                .subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void historicalMessages() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void historicalMessagesWithEndTimeAfter() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(topicMessage3.getConsensusTimestamp() + 1)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void historicalMessagesWithEndTimeEquals() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();
        var topicMessage4 = domainBuilder.topicMessage().block();

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(topicMessage4.getConsensusTimestamp())
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void historicalMessagesWithEndTimeExceedsPageSize() {
        int oldMaxPageSize = retrieverProperties.getMaxPageSize();
        retrieverProperties.setMaxPageSize(1);

        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        var topicMessage3 = domainBuilder.topicMessage().block();
        var topicMessage4 = domainBuilder.topicMessage().block();

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(topicMessage4.getConsensusTimestamp())
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .expectComplete()
                .verify(WAIT);

        retrieverProperties.setMaxPageSize(oldMaxPageSize);
    }

    @Test
    void historicalMessagesWithLimit() {
        var topicMessage1 = domainBuilder.topicMessage().block();
        var topicMessage2 = domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        var filter = TopicMessageFilter.builder()
                .limit(2)
                .startTime(0)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> topicMessageService.subscribeTopic(filter))
                .thenAwait(WAIT)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void incomingMessages() {
        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void incomingMessagesWithLimit() {
        var filter = TopicMessageFilter.builder()
                .limit(2)
                .startTime(0)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void incomingMessagesWithEndTimeBefore() {
        long endTime = now + 5_000_000_000L;
        var generator = domainBuilder.topicMessages(2, endTime - 2L);

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(endTime)
                .topicId(TOPIC_ID)
                .build();

        topicMessageService
                .subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void incomingMessagesWithEndTimeEquals() {
        var generator = domainBuilder.topicMessages(3, future - 2);

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .endTime(future)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void bothMessages() {
        domainBuilder.topicMessages(3, now).blockLast();

        var filter = TopicMessageFilter.builder()
                .limit(5)
                .startTime(0)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L, 3L, 4L, 5L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void bothMessagesWithTopicId() {
        domainBuilder.entity(e -> e.num(1L).id(1L)).block();
        domainBuilder.entity(e -> e.num(2L).id(2L)).block();
        domainBuilder
                .topicMessage(t -> t.topicId(EntityId.of(1L)).sequenceNumber(1))
                .block();
        domainBuilder
                .topicMessage(t -> t.topicId(EntityId.of(2L)).sequenceNumber(1))
                .block();

        var generator = Flux.concat(
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(1L)).sequenceNumber(2).consensusTimestamp(future + 1)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(2L)).sequenceNumber(2).consensusTimestamp(future + 2)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(3L)).sequenceNumber(1).consensusTimestamp(future + 3)));

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(1L))
                .build();

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void duplicateMessages() {
        var topicListener = Mockito.mock(TopicListener.class);
        var entityRepository = Mockito.mock(EntityRepository.class);
        var topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(
                new GrpcProperties(),
                topicListener,
                entityRepository,
                topicMessageRetriever,
                new SimpleMeterRegistry());

        var retrieverFilter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        Mockito.when(entityRepository.findById(retrieverFilter.getTopicId().getId()))
                .thenReturn(optionalEntity());

        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(true)))
                .thenReturn(Flux.just(topicMessage(1, 0), topicMessage(1, 1), topicMessage(2, 2), topicMessage(1, 3)));
        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(false)))
                .thenReturn(Flux.empty());

        Mockito.when(topicListener.listen(ArgumentMatchers.any())).thenReturn(Flux.empty());

        StepVerifier.withVirtualTime(() ->
                        topicMessageService.subscribeTopic(retrieverFilter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void missingMessages() {
        var topicListener = Mockito.mock(TopicListener.class);
        var entityRepository = Mockito.mock(EntityRepository.class);
        var topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(
                new GrpcProperties(),
                topicListener,
                entityRepository,
                topicMessageRetriever,
                new SimpleMeterRegistry());

        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        var beforeMissing = topicMessage(1);
        var afterMissing = topicMessage(4);

        Mockito.when(entityRepository.findById(filter.getTopicId().getId())).thenReturn(optionalEntity());
        Mockito.when(topicMessageRetriever.retrieve(filter, true)).thenReturn(Flux.empty());
        Mockito.when(topicListener.listen(filter)).thenReturn(Flux.just(beforeMissing, afterMissing));
        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.argThat(t -> t.getLimit() == 2
                                && t.getStartTime() == beforeMissing.getConsensusTimestamp() + 1
                                && t.getEndTime() == afterMissing.getConsensusTimestamp()),
                        ArgumentMatchers.eq(false)))
                .thenReturn(Flux.just(topicMessage(2), topicMessage(3)));

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void missingMessagesFromListenerAllRetrieved() {
        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        missingMessagesFromListenerTest(filter, Flux.just(topicMessage(5), topicMessage(6), topicMessage(7)));

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void missingMessagesFromListenerSomeRetrieved() {
        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        missingMessagesFromListenerTest(filter, Flux.just(topicMessage(5), topicMessage(6)));

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L)
                .expectError(IllegalStateException.class)
                .verify(WAIT);
    }

    @Test
    void missingMessagesFromListenerNoneRetrieved() {
        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        missingMessagesFromListenerTest(filter, Flux.empty());

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L)
                .expectError(IllegalStateException.class)
                .verify(WAIT);
    }

    @Test
    void missingMessagesFromRetrieverAndListener() {
        var topicListener = Mockito.mock(TopicListener.class);
        var entityRepository = Mockito.mock(EntityRepository.class);
        var topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(
                new GrpcProperties(),
                topicListener,
                entityRepository,
                topicMessageRetriever,
                new SimpleMeterRegistry());

        var retrieverFilter =
                TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();

        var retrieved1 = topicMessage(1);
        var retrieved2 = topicMessage(2);

        var beforeMissing1 = topicMessage(3);
        var beforeMissing2 = topicMessage(4);
        var afterMissing1 = topicMessage(8);
        var afterMissing2 = topicMessage(9);
        var afterMissing3 = topicMessage(10);

        Mockito.when(entityRepository.findById(retrieverFilter.getTopicId().getId()))
                .thenReturn(optionalEntity());

        var listenerFilter = TopicMessageFilter.builder()
                .startTime(retrieved2.getConsensusTimestamp())
                .build();

        Mockito.when(topicListener.listen(
                        ArgumentMatchers.argThat(l -> l.getStartTime() == listenerFilter.getStartTime())))
                .thenReturn(Flux.just(beforeMissing1, beforeMissing2, afterMissing1, afterMissing2, afterMissing3));

        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(true)))
                .thenReturn(Flux.just(retrieved1));
        var missing = Flux.just(topicMessage(5), topicMessage(6), topicMessage(7));
        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(false)))
                .thenReturn(Flux.just(retrieved2))
                .thenReturn(missing);

        StepVerifier.withVirtualTime(() ->
                        topicMessageService.subscribeTopic(retrieverFilter).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void receivedMessagesBeforeQueryCompletion() {
        var topicListener = Mockito.mock(TopicListener.class);
        var entityRepository = Mockito.mock(EntityRepository.class);
        var topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(
                new GrpcProperties(),
                topicListener,
                entityRepository,
                topicMessageRetriever,
                new SimpleMeterRegistry());

        var filter = TopicMessageFilter.builder().startTime(0).topicId(TOPIC_ID).build();
        var newFilter = filter.toBuilder().startTime(2L).build();

        var message1 = topicMessage(1);
        var message2 = topicMessage(2);
        var message3 = topicMessage(3);

        Mockito.when(entityRepository.findById(filter.getTopicId().getId())).thenReturn(optionalEntity());
        Mockito.when(topicMessageRetriever.retrieve(
                        Mockito.any(TopicMessageFilter.class), ArgumentMatchers.anyBoolean()))
                .thenReturn(Flux.defer(() -> Flux.just(message1).delayElements(Duration.ofMillis(500))));
        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.argThat(t -> t.getStartTime() == message1.getConsensusTimestamp() + 1),
                        ArgumentMatchers.eq(false)))
                .thenReturn(Flux.just(message2, message3));

        Sinks.Many<TopicMessage> liveSink = Sinks.many().multicast().onBackpressureBuffer();
        Mockito.when(topicListener.listen(filter)).thenReturn(liveSink.asFlux());
        Mockito.when(topicListener.listen(newFilter)).thenReturn(Flux.empty());

        StepVerifier.withVirtualTime(
                        () -> topicMessageService.subscribeTopic(filter).map(TopicMessage::getSequenceNumber))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(100)) // We are 100ms into the 500ms DB wait
                .then(() -> {
                    // Emit message before the DB query is finished.
                    log.info("Emit live message 3");
                    liveSink.tryEmitNext(message3);
                })
                .thenAwait(Duration.ofMillis(400)) // Finish the remaining DB wait time
                .thenAwait(Duration.ofSeconds(1)) // Await the safety check to finish
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verify();
    }

    private void missingMessagesFromListenerTest(TopicMessageFilter filter, Flux<TopicMessage> missingMessages) {
        var topicListener = Mockito.mock(TopicListener.class);
        var entityRepository = Mockito.mock(EntityRepository.class);
        var topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(
                new GrpcProperties(),
                topicListener,
                entityRepository,
                topicMessageRetriever,
                new SimpleMeterRegistry());

        // historic messages
        var retrieved1 = topicMessage(1);
        var retrieved2 = topicMessage(2);

        // incoming messages before gap
        var beforeMissing1 = topicMessage(3);
        var beforeMissing2 = topicMessage(4);

        // incoming messages after gap
        var afterMissing1 = topicMessage(8);
        var afterMissing2 = topicMessage(9);
        var afterMissing3 = topicMessage(10);

        // mock entity type check
        Mockito.when(entityRepository.findById(filter.getTopicId().getId())).thenReturn(optionalEntity());
        Mockito.when(topicMessageRetriever.retrieve(filter, true)).thenReturn(Flux.just(retrieved1, retrieved2));

        var listenerFilter = TopicMessageFilter.builder()
                .startTime(beforeMissing1.getConsensusTimestamp())
                .build();

        Mockito.when(topicListener.listen(
                        ArgumentMatchers.argThat(l -> l.getStartTime() == listenerFilter.getStartTime())))
                .thenReturn(Flux.just(beforeMissing1, beforeMissing2, afterMissing1, afterMissing2, afterMissing3));
        Mockito.when(topicMessageRetriever.retrieve(
                        ArgumentMatchers.argThat(t -> t.getLimit() == 3
                                && t.getStartTime() == beforeMissing2.getConsensusTimestamp() + 1
                                && t.getEndTime() == afterMissing1.getConsensusTimestamp()),
                        ArgumentMatchers.eq(false)))
                .thenReturn(missingMessages);
    }

    private TopicMessage topicMessage(long sequenceNumber) {
        return topicMessage(sequenceNumber, sequenceNumber);
    }

    private TopicMessage topicMessage(long sequenceNumber, long consensusTimestamp) {
        return TopicMessage.builder()
                .consensusTimestamp(consensusTimestamp)
                .sequenceNumber(sequenceNumber)
                .message(new byte[] {0, 1, 2})
                .runningHash(new byte[] {3, 4, 5})
                .topicId(TOPIC_ID)
                .runningHashVersion(2)
                .build();
    }

    private Optional<Entity> optionalEntity() {
        return Optional.of(Entity.builder().type(EntityType.TOPIC).memo("memo").build());
    }
}
