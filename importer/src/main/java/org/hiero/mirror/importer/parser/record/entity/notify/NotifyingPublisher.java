// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.notify;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.Collection;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.parser.record.entity.BatchPublisher;
import org.hiero.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

@ConditionOnEntityRecordParser
@CustomLog
@Named
@Order(1)
public class NotifyingPublisher implements BatchPublisher {

    private static final String SQL = "select pg_notify('topic_message', ?)";

    private final NotifyProperties notifyProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ParserContext parserContext;
    private final Timer timer;

    NotifyingPublisher(
            NotifyProperties notifyProperties,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry,
            ParserContext parserContext) {
        this.notifyProperties = notifyProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.parserContext = parserContext;
        this.timer = PUBLISH_TIMER.tag("type", "notify").register(meterRegistry);
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        if (!notifyProperties.isEnabled()) {
            return;
        }

        var topicMessages = parserContext.get(TopicMessage.class);
        if (topicMessages.isEmpty()) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        timer.record(() -> jdbcTemplate.execute(SQL, callback(topicMessages)));
        log.info("Finished notifying {} messages in {}", topicMessages.size(), stopwatch);
    }

    private PreparedStatementCallback<int[]> callback(Collection<TopicMessage> topicMessages) {
        return preparedStatement -> {
            for (TopicMessage topicMessage : topicMessages) {
                String json = toJson(topicMessage);
                if (json != null) {
                    preparedStatement.setString(1, json);
                    preparedStatement.addBatch();
                }
            }
            return preparedStatement.executeBatch();
        };
    }

    private String toJson(TopicMessage topicMessage) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(topicMessage);

            if (json.length() >= notifyProperties.getMaxJsonPayloadSize()) {
                log.warn("Unable to notify large payload of size {}B: {}", json.length(), topicMessage);
                return null;
            }

            return json;
        } catch (Exception e) {
            Utility.handleRecoverableError("Error serializing topicMessage to json", topicMessage, e);
            return null;
        }
    }
}
