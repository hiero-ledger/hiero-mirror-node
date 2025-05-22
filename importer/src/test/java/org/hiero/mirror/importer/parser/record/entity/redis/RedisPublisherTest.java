// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.topic.StreamMessage;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RedisPublisherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2L);
    private static final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private RedisOperations<String, StreamMessage> redisOperations;

    private RedisPublisher entityListener;
    private ParserContext parserContext;
    private RedisProperties redisProperties;

    @BeforeEach
    void setup() {
        parserContext = new ParserContext();
        redisProperties = new RedisProperties();
        entityListener = new RedisPublisher(redisProperties, redisOperations, new SimpleMeterRegistry(), parserContext);
    }

    @Test
    void onSlowPublish() {
        // given
        int publishCount = redisProperties.getQueueCapacity() + 2;
        Sinks.Many<Object> sink = Sinks.many().multicast().directBestEffort();
        Flux<Integer> publisher = Flux.range(1, publishCount).doOnNext(i -> {
            submitAndSave(topicMessage());
        });

        // when
        when(redisOperations.executePipelined(any(SessionCallback.class))).then(callback -> {
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(50L));
            sink.tryEmitNext(callback);
            return null;
        });

        // then
        StepVerifier redisVerifier = sink.asFlux()
                .subscribeOn(Schedulers.parallel())
                .as(StepVerifier::create)
                .expectNextCount(publishCount)
                .thenCancel()
                .verifyLater();

        publisher
                .publishOn(Schedulers.parallel())
                .as(StepVerifier::create)
                .expectNextCount(publishCount)
                .expectComplete()
                .verify(TIMEOUT);

        redisVerifier.verify(TIMEOUT);
        verify(redisOperations, timeout(TIMEOUT.toMillis() * 5).times(publishCount))
                .executePipelined(any(SessionCallback.class));
    }

    @Test
    void onNonDuplicateTopicMessages() {
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();

        // submitAndSave two messages, verify publish logic called twice
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(TIMEOUT.toMillis()).times(2)).executePipelined(any(SessionCallback.class));
    }

    protected TopicMessage topicMessage() {
        return domainBuilder.topicMessage().get();
    }

    private void submitAndSave(TopicMessage topicMessage) {
        parserContext.add(topicMessage);
        entityListener.onEnd(null);
    }
}
