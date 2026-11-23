// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.retriever.RetrieverProperties.MAX_PAGE_SIZE;
import static org.hiero.mirror.grpc.retriever.RetrieverProperties.MIN_PAGE_SIZE;

import java.util.List;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The topic messages by topic ID query has a variant with and without the optional end time, instead of a nullable end
 * time parameter, so the end time condition is only applied when present.
 */
public interface TopicMessageRepository extends CrudRepository<TopicMessage, Long> {

    String ORDER_BY_TIMESTAMP = """
            order by consensus_timestamp asc
            limit :limit
            """;

    String SELECT_BY_TOPIC_ID = """
            select *
            from topic_message
            where topic_id = :topicId
              and consensus_timestamp >= :startTime
            """;

    @Transactional(readOnly = true)
    default Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        final var endTime = filter.getEndTime();
        final long topicId = filter.getTopicId().getId();
        final long startTime = filter.getStartTime();
        final int limit = (int) Math.max(Math.min(filter.getLimit(), MAX_PAGE_SIZE), MIN_PAGE_SIZE);

        if (filter.getLimit() != 1) {
            lowerRandomPageCost();
        }

        // getResultStream()'s cursor doesn't work with reactive streams, so stream the list
        final var topicMessages = endTime != null
                ? findByTopicIdAndTimestamp(topicId, startTime, endTime, limit)
                : findByTopicIdAndTimestamp(topicId, startTime, limit);
        return topicMessages.stream();
    }

    @Query(value = SELECT_BY_TOPIC_ID + ORDER_BY_TIMESTAMP, nativeQuery = true)
    List<TopicMessage> findByTopicIdAndTimestamp(long topicId, long startTime, int limit);

    @Query(
            value = SELECT_BY_TOPIC_ID + "  and consensus_timestamp < :endTime\n" + ORDER_BY_TIMESTAMP,
            nativeQuery = true)
    List<TopicMessage> findByTopicIdAndTimestamp(long topicId, long startTime, long endTime, int limit);

    @Query(value = """
       with related_transaction as (
         select entity_id as topic_id, consensus_timestamp
         from transaction
         where consensus_timestamp > :consensusTimestamp and result = 22 and type = 27
         order by consensus_timestamp
         limit :limit
       )
       select t.*
       from topic_message as t
       join related_transaction using (topic_id, consensus_timestamp)
       order by t.consensus_timestamp
       """, nativeQuery = true)
    List<TopicMessage> findLatest(long consensusTimestamp, int limit);

    // Make the cost estimation of using the index on (topic_id, consensus_timestamp) lower than that of the primary
    // key, so the planner chooses the better index when querying topic messages by id. It only lasts until the end of
    // the current transaction.
    @Modifying
    @Query(value = "set local random_page_cost = 0", nativeQuery = true)
    void lowerRandomPageCost();
}
