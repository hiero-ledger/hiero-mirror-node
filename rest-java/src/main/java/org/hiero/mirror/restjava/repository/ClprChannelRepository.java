// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Queries filtered by {@code channel_id}, the distribution column in {@code v2}, route to a single shard. The others
 * scatter-gather across all shards. {@code created_timestamp} is unique per channel, so it's the sole sort key. A
 * lookup by {@code channel_id} alone is {@link #findById(Object)}. All bounds are inclusive and computed by the
 * service.
 */
public interface ClprChannelRepository extends CrudRepository<ClprChannel, byte[]> {

    /**
     * Returns the channel's state as of the given bounds, from the current or the history table: the latest version
     * whose {@code timestamp_range} overlaps {@code [lowerBound, upperBound]}, then filtered by {@code status} if
     * given. The two tables share the same layout and a channel's timestamp ranges never overlap. Overlap is
     * expressed with explicit comparisons rather than {@code int8range(..., '[]')}, which overflows when the service
     * passes {@code Long.MAX_VALUE} as an open upper bound.
     */
    @Query(value = """
            select * from (
                select * from (
                    select * from clpr_channel
                    where channel_id = :channelId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                    union all
                    select * from clpr_channel_history
                    where channel_id = :channelId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                ) as v
                order by v.timestamp_range desc
                limit 1
            ) as c
            where cast(:status as clpr_channel_status) is null or c.status = cast(:status as clpr_channel_status)
            """, nativeQuery = true)
    List<ClprChannel> findByChannelIdAndTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, @Nullable String status);

    @Query(value = """
            select * from clpr_channel
            where created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and (cast(:status as clpr_channel_status) is null or status = cast(:status as clpr_channel_status))
            """, nativeQuery = true)
    List<ClprChannel> findByCreatedTimestampBetween(
            long lowerBound, long upperBound, @Nullable String status, Pageable pageable);
}
