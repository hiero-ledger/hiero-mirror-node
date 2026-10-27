// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Queries filtered by {@code channel_id}, the distribution column in {@code v2}, route to a single shard. The other
 * query scatter-gathers across all shards.
 */
public interface ClprConnectorRepository extends CrudRepository<ClprConnector, AbstractClprConnector.Id> {

    /**
     * Returns the connector's state as of the given timestamp, from the current or the history table. A connector's
     * timestamp ranges never overlap, so at most one row matches.
     */
    @Query(value = """
            select * from (
                select * from clpr_connector
                where channel_id = :channelId and connector_id = :connectorId
                and timestamp_range @> cast(:timestamp as bigint)
                union all
                select * from clpr_connector_history
                where channel_id = :channelId and connector_id = :connectorId
                and timestamp_range @> cast(:timestamp as bigint)
            ) as c
            where cast(:deleted as boolean) is null or coalesce(c.deleted, false) = :deleted
            limit 1
            """, nativeQuery = true)
    List<ClprConnector> findByChannelIdAndConnectorIdAndTimestamp(
            byte[] channelId, byte[] connectorId, long timestamp, @Nullable Boolean deleted);

    /**
     * Within a channel, the connector id and deleted filters are cheap filters on top of the channel_id lookup. A
     * connector that was never removed may have deleted unset, so it counts as not deleted.
     */
    @Query(value = """
            select * from clpr_connector
            where channel_id = :channelId
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and (cast(:connectorId as bytea) is null or connector_id = :connectorId)
            and (cast(:deleted as boolean) is null or coalesce(deleted, false) = :deleted)
            """, nativeQuery = true)
    List<ClprConnector> findByChannelId(
            byte[] channelId,
            long lowerBound,
            long upperBound,
            byte @Nullable [] connectorId,
            @Nullable Boolean deleted,
            Pageable pageable);

    @Query(value = """
            select * from clpr_connector
            where created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprConnector> findByCreatedTimestampBetween(long lowerBound, long upperBound, Pageable pageable);
}
