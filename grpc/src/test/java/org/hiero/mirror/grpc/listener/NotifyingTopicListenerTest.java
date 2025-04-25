// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class NotifyingTopicListenerTest extends AbstractSharedTopicListenerTest {

    private static final String JSON =
            """
                    {
                      "@type":"TopicMessage",
                      "chunk_num":1,
                      "chunk_total":2,
                      "consensus_timestamp":1594401417000000000,
                      "message":"AQID",
                      "payer_account_id":4294968296,
                      "running_hash":"BAUG",
                      "running_hash_version":2,
                      "sequence_number":1,
                      "topic_id":1001,
                      "valid_start_timestamp":1594401416000000000
                    }""";
    private static final Duration WAIT = Duration.ofSeconds(1L);
    private static boolean initialized = false;
    private final NotifyingTopicListener topicListener;
    private final JdbcTemplate jdbcTemplate;

    @Override
    protected ListenerProperties.ListenerType getType() {
        return ListenerProperties.ListenerType.NOTIFY;
    }

    @BeforeEach
    void warmup() {
        if (!initialized) {
            try {
                // Warm up the database connection
                var filter = TopicMessageFilter.builder().build();
                StepVerifier.withVirtualTime(() -> topicListener.listen(filter))
                        .thenAwait(WAIT)
                        .then(() -> jdbcTemplate.execute("notify topic_message, '" + JSON + "'"))
                        .expectNextCount(1)
                        .thenCancel()
                        .verify(WAIT);
                initialized = true;
            } catch (AssertionError e) {
                log.warn("Unable to warmup connection: {}", e.getMessage());
            }
        }
    }

    // Test deserialization from JSON to verify contract with PostgreSQL listen/notify
    @Test
    void json() {
        var topicMessage = TopicMessage.builder()
                .chunkNum(1)
                .chunkTotal(2)
                .consensusTimestamp(1594401417000000000L)
                .message(new byte[] {1, 2, 3})
                .payerAccountId(EntityId.of(4294968296L))
                .runningHash(new byte[] {4, 5, 6})
                .runningHashVersion(2)
                .sequenceNumber(1L)
                .topicId(EntityId.of(1001L))
                .validStartTimestamp(1594401416000000000L)
                .build();

        var filter = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(1001L))
                .build();

        StepVerifier.create(topicListener.listen(filter))
                .thenAwait(Duration.ofMillis(200L))
                .then(() -> jdbcTemplate.execute("notify topic_message, '" + JSON + "'"))
                .thenAwait(Duration.ofMillis(200L))
                .expectNext(topicMessage)
                .thenCancel()
                .verify(WAIT);
    }

    @Test
    void jsonError() {
        TopicMessageFilter filter = TopicMessageFilter.builder().startTime(0).build();

        // Parsing errors will be logged and ignored and the message will be lost
        StepVerifier.create(topicListener.listen(filter))
                .thenAwait(Duration.ofMillis(200L))
                .then(() -> jdbcTemplate.execute("notify topic_message, 'invalid'"))
                .thenAwait(Duration.ofMillis(500L))
                .expectNoEvent(Duration.ofMillis(500L))
                .thenCancel()
                .verify(WAIT);
    }

    @Override
    protected void publish(Flux<TopicMessage> publisher) {
        publisher
                .concatMap(t -> {
                    jdbcTemplate.queryForMap("select pg_notify('topic_message', ?)", toJson(t));
                    return Mono.just(t);
                })
                .blockLast();
    }

    private String toJson(TopicMessage topicMessage) {
        try {
            return topicListener.objectMapper.writeValueAsString(topicMessage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
