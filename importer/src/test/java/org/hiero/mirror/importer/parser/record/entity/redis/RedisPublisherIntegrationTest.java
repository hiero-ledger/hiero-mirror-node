// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.StreamMessage;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.BatchPublisherTest;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPublisherIntegrationTest extends BatchPublisherTest {

    private final ReactiveRedisOperations<String, StreamMessage> redisOperations;
    private final List<RecordStreamFileListener> streamFileListeners;

    public RedisPublisherIntegrationTest(
            RedisPublisher redisPublisher,
            ParserContext parserContext,
            RedisProperties properties,
            ReactiveRedisOperations<String, StreamMessage> redisOperations,
            List<RecordStreamFileListener> streamFileListeners) {
        super(redisPublisher, parserContext, properties);
        this.redisOperations = redisOperations;
        this.streamFileListeners = streamFileListeners;
    }

    @Override
    protected Flux<TopicMessage> subscribe(EntityId topicId) {
        return redisOperations.listenToChannel("topic." + topicId.getId()).map(m -> (TopicMessage) m.getMessage());
    }

    @Test
    void publishesFirst() {
        assertThat(streamFileListeners).first().isEqualTo(batchPublisher);
    }
}
