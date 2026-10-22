// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Every query is filtered by {@code channel_id}, the distribution column in {@code v2}, so it routes to a single
 * shard. The join to {@code clpr_channel} is on that same column, so it stays on that shard too. {@code clpr_message}
 * is also time-partitioned by {@code consensus_timestamp}, so the plain timestamp range is repeated alongside the
 * compound {@code (consensus_timestamp, message_id)} range to allow partition pruning. Callers should sort by
 * {@code consensus_timestamp} then {@code message_id}.
 * <p>
 * The select order must match the {@link ClprMessageDto} record components, which are mapped by position.
 */
public interface ClprMessageRepository extends CrudRepository<ClprMessage, ClprMessage.Id> {

    @Query(value = """
            select
              m.message_id <= c.acked_message_id as acknowledged,
              m.channel_id as channelId,
              m.connector_id as connectorId,
              m.consensus_timestamp as consensusTimestamp,
              m.message_data as messageData,
              m.message_id as messageId,
              m.message_type::text as messageType,
              m.reply_status::text as replyStatus,
              m.reply_to_message_id as replyToMessageId,
              m.running_hash as runningHash,
              m.sender as sender,
              m.target_application as targetApplication
            from clpr_message m
            join clpr_channel c on c.channel_id = m.channel_id
            where m.channel_id = :channelId
            and m.consensus_timestamp >= :timestampLowerBound
            and m.consensus_timestamp <= :timestampUpperBound
            and (m.consensus_timestamp, m.message_id) >= (:timestampLowerBound, :messageIdLowerBound)
            and (m.consensus_timestamp, m.message_id) <= (:timestampUpperBound, :messageIdUpperBound)
            and (cast(:type as clpr_message_type) is null or m.message_type = cast(:type as clpr_message_type))
            """, nativeQuery = true)
    @SuppressWarnings("java:S107")
    List<ClprMessageDto> findByChannelId(
            byte[] channelId,
            long timestampLowerBound,
            long timestampUpperBound,
            long messageIdLowerBound,
            long messageIdUpperBound,
            @Nullable String type,
            Pageable pageable);

    /**
     * Served by the partial clpr_message__connector_id index, which only holds DATA messages, so it needs no timestamp
     * bound of its own.
     */
    @Query(value = """
            select
              m.message_id <= c.acked_message_id as acknowledged,
              m.channel_id as channelId,
              m.connector_id as connectorId,
              m.consensus_timestamp as consensusTimestamp,
              m.message_data as messageData,
              m.message_id as messageId,
              m.message_type::text as messageType,
              m.reply_status::text as replyStatus,
              m.reply_to_message_id as replyToMessageId,
              m.running_hash as runningHash,
              m.sender as sender,
              m.target_application as targetApplication
            from clpr_message m
            join clpr_channel c on c.channel_id = m.channel_id
            where m.connector_id = :connectorId
            and m.channel_id = :channelId
            and m.consensus_timestamp >= :timestampLowerBound
            and m.consensus_timestamp <= :timestampUpperBound
            and (m.consensus_timestamp, m.message_id) >= (:timestampLowerBound, :messageIdLowerBound)
            and (m.consensus_timestamp, m.message_id) <= (:timestampUpperBound, :messageIdUpperBound)
            and (cast(:type as clpr_message_type) is null or m.message_type = cast(:type as clpr_message_type))
            """, nativeQuery = true)
    @SuppressWarnings("java:S107")
    List<ClprMessageDto> findByChannelIdAndConnectorId(
            byte[] channelId,
            byte[] connectorId,
            long timestampLowerBound,
            long timestampUpperBound,
            long messageIdLowerBound,
            long messageIdUpperBound,
            @Nullable String type,
            Pageable pageable);
}
