// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Queries filtered by {@code channel_id}, the distribution column in {@code v2}, route to a single shard. The others
 * scatter-gather across all shards. {@code created_timestamp} is unique per channel, so it's the sole sort key.
 */
public interface ClprChannelRepository extends CrudRepository<ClprChannel, byte[]> {

    @Query(value = """
            select * from clpr_channel
            where channel_id = :channelId
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprChannel> findByChannelIdAndCreatedTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, Pageable pageable);

    @Query(value = """
            select * from clpr_channel
            where channel_id = :channelId
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and status = cast(:status as clpr_channel_status)
            """, nativeQuery = true)
    List<ClprChannel> findByChannelIdAndCreatedTimestampBetweenAndStatus(
            byte[] channelId, long lowerBound, long upperBound, String status, Pageable pageable);

    @Query(value = """
            select * from clpr_channel
            where created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprChannel> findByCreatedTimestampBetween(long lowerBound, long upperBound, Pageable pageable);

    @Query(value = """
            select * from clpr_channel
            where created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and status = cast(:status as clpr_channel_status)
            """, nativeQuery = true)
    List<ClprChannel> findByCreatedTimestampBetweenAndStatus(
            long lowerBound, long upperBound, String status, Pageable pageable);
}
