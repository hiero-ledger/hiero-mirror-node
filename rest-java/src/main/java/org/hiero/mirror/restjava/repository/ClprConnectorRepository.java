// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Every query is filtered by {@code channel_id}, the distribution column in {@code v2}, so it's routed to a single
 * shard. {@code created_timestamp} is the sort key of the list query. All bounds are inclusive and computed by the
 * service.
 */
public interface ClprConnectorRepository extends Repository<ClprConnector, AbstractClprConnector.Id> {

    /** The columns of {@code clpr_connector}, which {@code clpr_connector_history} shares, for use as a select list. */
    String CONNECTOR_COLUMNS = """
            admin_key,
            channel_id,
            commitment,
            connector_contract_id,
            connector_id,
            created_timestamp,
            in_flight_message_count,
            locked_stake,
            public_key,
            salt,
            signature,
            signature_scheme,
            slash_count,
            stake_recipient_id,
            status,
            timestamp_range
            """;

    /** Returns the connector's current state, or null if there is none. */
    @Query(value = """
            select
            """ + CONNECTOR_COLUMNS + """
            from clpr_connector
            where channel_id = :channelId
            and connector_id = :connectorId
            """, nativeQuery = true)
    @Nullable
    ClprConnector findByChannelIdAndConnectorId(byte[] channelId, byte[] connectorId);

    /**
     * Returns the connector's state as of the given bounds, from the current or the history table: the latest version
     * whose {@code timestamp_range} overlaps {@code [lowerBound, upperBound]}, then filtered by {@code status} if
     * given. Overlap is expressed with explicit comparisons rather than {@code int8range(..., '[]')}, which overflows
     * when the service passes {@code Long.MAX_VALUE} as an open upper bound.
     */
    @Query(value = """
            select * from (
                select * from (
                    select
            """ + CONNECTOR_COLUMNS + """
                    from clpr_connector
                    where channel_id = :channelId
                    and connector_id = :connectorId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                    union all
                    select
            """ + CONNECTOR_COLUMNS + """
                    from clpr_connector_history
                    where channel_id = :channelId
                    and connector_id = :connectorId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                ) as v
                order by v.timestamp_range desc
                limit 1
            ) as c
            where cast(:status as varchar) is null or cast(c.status as varchar) = cast(:status as varchar)
            """, nativeQuery = true)
    List<ClprConnector> findByChannelIdAndConnectorIdAndTimestampBetween(
            byte[] channelId, byte[] connectorId, long lowerBound, long upperBound, @Nullable String status);

    /** Returns the channel's connectors, including the pending ones, whose creation timestamp is within the bounds. */
    @Query(value = """
            select
            """ + CONNECTOR_COLUMNS + """
            from clpr_connector
            where channel_id = :channelId
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and (cast(:status as clpr_connector_status) is null or status = cast(:status as clpr_connector_status))
            """, nativeQuery = true)
    List<ClprConnector> findByChannelIdAndCreatedTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, @Nullable String status, Pageable pageable);
}
