// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Every query is filtered by {@code channel_id}, the distribution column in {@code v2}, so it's routed to a single
 * shard, and by a {@code consensus_timestamp} range, which prunes the time partitions. {@code consensus_timestamp} is
 * the sort key. All bounds are inclusive and computed by the service.
 */
public interface ClprMessageRepository extends Repository<ClprMessage, ClprMessage.Id> {

    /**
     * The columns of {@code clpr_message} as {@link ClprMessageDto}, for use as a select list. A message is
     * acknowledged when its id is at most the channel's {@code acked_message_id}, and never when the channel is
     * unknown. Queries must left join {@code clpr_channel} as {@code c} on the channel id.
     */
    String MESSAGE_COLUMNS = """
            coalesce(m.message_id <= c.acked_message_id, false) as acknowledged,
            m.channel_id as channelId,
            m.connector_id as connectorId,
            m.consensus_timestamp as consensusTimestamp,
            m.message_data as messageData,
            m.message_id as messageId,
            cast(m.message_type as text) as messageType,
            cast(m.reply_status as text) as replyStatus,
            m.reply_to_message_id as replyToMessageId,
            m.running_hash as runningHash,
            m.sender as sender,
            m.target_application as targetApplication
            """;

    /**
     * Returns the channel's messages dispatched for the connector within the bounds, optionally of the given type,
     * served by the {@code (channel_id, connector_id, consensus_timestamp)} index.
     */
    @Query(value = """
            select
            """ + MESSAGE_COLUMNS + """
            from clpr_message m
            left join clpr_channel c on c.channel_id = m.channel_id
            where m.channel_id = :channelId
            and m.connector_id = :connectorId
            and m.consensus_timestamp >= :lowerBound
            and m.consensus_timestamp <= :upperBound
            and (cast(:type as clpr_message_type) is null or m.message_type = cast(:type as clpr_message_type))
            """, nativeQuery = true)
    List<ClprMessageDto> findByChannelIdAndConnectorIdAndTimestampBetween(
            byte[] channelId,
            byte[] connectorId,
            long lowerBound,
            long upperBound,
            @Nullable String type,
            Pageable pageable);

    /**
     * Returns the channel's messages within the bounds, optionally of the given type, served by the
     * {@code (channel_id, consensus_timestamp)} index.
     */
    @Query(value = """
            select
            """ + MESSAGE_COLUMNS + """
            from clpr_message m
            left join clpr_channel c on c.channel_id = m.channel_id
            where m.channel_id = :channelId
            and m.consensus_timestamp >= :lowerBound
            and m.consensus_timestamp <= :upperBound
            and (cast(:type as clpr_message_type) is null or m.message_type = cast(:type as clpr_message_type))
            """, nativeQuery = true)
    List<ClprMessageDto> findByChannelIdAndTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, @Nullable String type, Pageable pageable);
}
