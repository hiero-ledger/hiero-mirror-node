// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/** Every query is filtered by {@code channel_id}, the distribution column in {@code v2}, so it routes to a single shard. */
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
            limit 1
            """, nativeQuery = true)
    List<ClprConnector> findByChannelIdAndConnectorIdAndTimestamp(byte[] channelId, byte[] connectorId, long timestamp);

    /** Within a channel, the connector id filter is a cheap filter on top of the channel_id lookup. */
    @Query(value = """
            select * from clpr_connector
            where channel_id = :channelId
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and (cast(:connectorId as bytea) is null or connector_id = :connectorId)
            """, nativeQuery = true)
    List<ClprConnector> findByChannelId(
            byte[] channelId, long lowerBound, long upperBound, byte @Nullable [] connectorId, Pageable pageable);
}
