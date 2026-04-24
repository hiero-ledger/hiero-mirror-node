// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@CustomLog
@Named
@RequiredArgsConstructor
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {

    private static final String COLUMN_CONSENSUS_TIMESTAMP = "consensus_timestamp";
    private static final String COLUMN_TOPIC_ID = "topic_id";
    private static final String TOPIC_MESSAGES_BY_ID_QUERY_HINT = "set local random_page_cost = 0";
    private static final RowMapper<TopicMessage> ROW_MAPPER = new BeanPropertyRowMapper<>(TopicMessage.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        StringBuilder sql = new StringBuilder("select * from topic_message where ")
                .append(COLUMN_TOPIC_ID)
                .append(" = ? and ")
                .append(COLUMN_CONSENSUS_TIMESTAMP)
                .append(" >= ?");

        List<Object> params = new ArrayList<>();
        params.add(filter.getTopicId());
        params.add(filter.getStartTime());

        if (filter.getEndTime() != null) {
            sql.append(" and ").append(COLUMN_CONSENSUS_TIMESTAMP).append(" < ?");
            params.add(filter.getEndTime());
        }

        sql.append(" order by ").append(COLUMN_CONSENSUS_TIMESTAMP).append(" asc");

        if (filter.hasLimit()) {
            sql.append(" limit ?");
            params.add(filter.getLimit());
        }

        if (filter.getLimit() != 1) {
            jdbcTemplate.execute(TOPIC_MESSAGES_BY_ID_QUERY_HINT);
        }

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray()).stream();
    }
}
