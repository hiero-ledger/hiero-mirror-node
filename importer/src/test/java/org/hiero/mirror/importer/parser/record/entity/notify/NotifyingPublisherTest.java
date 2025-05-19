// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.notify;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;
import static org.hiero.mirror.common.util.CommonUtils.nextBytes;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import javax.sql.DataSource;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.importer.parser.record.entity.BatchPublisherTest;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgConnection;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class NotifyingPublisherTest extends BatchPublisherTest {

    private final DataSource dataSource;

    public NotifyingPublisherTest(
            NotifyingPublisher entityListener,
            ParserContext parserContext,
            NotifyProperties properties,
            DataSource dataSource) {
        super(entityListener, parserContext, properties);
        this.dataSource = dataSource;
    }

    @Test
    void onTopicMessagePayloadTooLong() throws InterruptedException {
        // given
        TopicMessage topicMessage = domainBuilder.topicMessage().get();
        topicMessage.setMessage(nextBytes(5824)); // Just exceeds 8000B
        var topicMessages = subscribe(topicMessage.getTopicId());

        // when
        parserContext.add(topicMessage);
        batchPublisher.onEnd(null);

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Override
    protected Flux<TopicMessage> subscribe(EntityId topicId) {
        try {
            var connection = dataSource.getConnection();
            var pgConnection = connection.unwrap(PgConnection.class);
            pgConnection.execSQLUpdate("listen topic_message");
            return Flux.defer(() -> getNotifications(pgConnection, topicId))
                    .repeat()
                    .subscribeOn(Schedulers.parallel())
                    .timeout(Duration.ofSeconds(3))
                    .doFinally(s -> {
                        try {
                            connection.close();
                        } catch (SQLException e) {
                            // Ignore
                        }
                    })
                    .doOnSubscribe(s -> log.info("Subscribed to {}", topicId));
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private Flux<TopicMessage> getNotifications(PgConnection pgConnection, EntityId topicId) {
        try {
            var topicMessages = new ArrayList<TopicMessage>();
            var notifications = pgConnection.getNotifications(100);

            if (notifications != null) {
                for (var pgNotification : notifications) {
                    var topicMessage = OBJECT_MAPPER.readValue(pgNotification.getParameter(), TopicMessage.class);
                    if (topicId.equals(topicMessage.getTopicId())) {
                        topicMessages.add(topicMessage);
                    }
                }
            }
            return Flux.fromIterable(topicMessages);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }
}
