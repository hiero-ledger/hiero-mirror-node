// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import jakarta.inject.Named;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@CustomLog
@Named
@RequiredArgsConstructor
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {

    // make the cost estimation of using the index on (topic_id, consensus_timestamp) lower than that of
    // the primary key so pg planner will choose the better index when querying topic messages by id
    private static final String TOPIC_MESSAGES_BY_ID_QUERY_HINT = "set local random_page_cost = 0";

    private static final RowMapper<TopicMessage> ROW_MAPPER = (rs, rowNum) -> TopicMessage.builder()
            .chunkNum(rs.getObject("chunk_num", Integer.class))
            .chunkTotal(rs.getObject("chunk_total", Integer.class))
            .consensusTimestamp(rs.getLong("consensus_timestamp"))
            .initialTransactionId(rs.getBytes("initial_transaction_id"))
            .message(rs.getBytes("message"))
            .payerAccountId(getEntityId(rs, "payer_account_id"))
            .runningHash(rs.getBytes("running_hash"))
            .runningHashVersion(rs.getObject("running_hash_version", Integer.class))
            .sequenceNumber(rs.getLong("sequence_number"))
            .topicId(getEntityId(rs, "topic_id"))
            .validStartTimestamp(rs.getObject("valid_start_timestamp", Long.class))
            .build();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        var sql = new StringBuilder("""
                select * from topic_message
                where topic_id = :topicId
                and consensus_timestamp >= :startTime
                """);

        var params = new MapSqlParameterSource()
                .addValue("topicId", filter.getTopicId().getId())
                .addValue("startTime", filter.getStartTime());

        if (filter.getEndTime() != null) {
            sql.append("and consensus_timestamp < :endTime\n");
            params.addValue("endTime", filter.getEndTime());
        }

        sql.append("order by consensus_timestamp asc");

        if (filter.hasLimit()) {
            sql.append("\nlimit :limit");
            params.addValue("limit", filter.getLimit());
        }

        if (filter.getLimit() != 1) {
            // only apply the hint when limit is not 1
            jdbcTemplate.getJdbcOperations().execute(TOPIC_MESSAGES_BY_ID_QUERY_HINT);
        }

        return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER).stream();
    }

    private static EntityId getEntityId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : EntityId.of(value);
    }
}
