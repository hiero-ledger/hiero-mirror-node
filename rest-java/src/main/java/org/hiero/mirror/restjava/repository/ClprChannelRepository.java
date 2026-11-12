// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannelData;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Every method returns {@link ClprChannelData}, whose {@code status} also holds {@code PENDING}, so the queries
 * select every column with the status cast to text. Queries filtered by {@code channel_id}, the distribution column
 * in {@code v2}, route to a single shard. The others scatter-gather across all shards. {@code created_timestamp} is
 * unique per channel, so it's the sole sort key. All bounds are inclusive and computed by the service.
 */
public interface ClprChannelRepository extends Repository<ClprChannelData, byte[]> {

    /** The columns of {@code clpr_channel} as {@link ClprChannelData}, for use as a select list. */
    String CHANNEL_COLUMNS = """
            acked_message_id,
            chain_id,
            channel_context,
            channel_id,
            config_proof_bytes,
            created_timestamp,
            endpoint_manifest,
            endpoint_manifest_proof_bytes,
            endpoint_manifest_version,
            last_config_timestamp,
            next_message_id,
            ownership_commitment,
            peer_config_timestamp,
            peer_max_gas_per_message,
            peer_max_local_endpoints,
            peer_max_message_payload_bytes,
            peer_max_messages_per_bundle,
            peer_max_peer_endpoints,
            peer_max_queue_depth,
            peer_max_sync_bytes,
            public_key,
            received_message_id,
            received_running_hash,
            sent_running_hash,
            signature,
            signature_scheme,
            cast(status as varchar) as status,
            timestamp_range,
            trust_anchor,
            trust_anchor_id,
            verifier_contract_id,
            verifier_fingerprint
            """;

    /**
     * The columns of {@code clpr_channel_pending_commitment} as {@link ClprChannelData}, for use as a select list. The
     * columns the table doesn't have, including {@code timestamp_range}, are null and {@code status} is {@code PENDING}.
     */
    String PENDING_COLUMNS = """
            cast(null as bigint) as acked_message_id,
            cast(null as varchar) as chain_id,
            cast(null as bytea) as channel_context,
            cast(null as bytea) as channel_id,
            cast(null as bytea) as config_proof_bytes,
            created_timestamp,
            cast(null as jsonb) as endpoint_manifest,
            cast(null as bytea) as endpoint_manifest_proof_bytes,
            cast(null as bigint) as endpoint_manifest_version,
            cast(null as bigint) as last_config_timestamp,
            cast(null as bigint) as next_message_id,
            ownership_commitment,
            cast(null as bigint) as peer_config_timestamp,
            cast(null as bigint) as peer_max_gas_per_message,
            cast(null as bigint) as peer_max_local_endpoints,
            cast(null as bigint) as peer_max_message_payload_bytes,
            cast(null as bigint) as peer_max_messages_per_bundle,
            cast(null as bigint) as peer_max_peer_endpoints,
            cast(null as bigint) as peer_max_queue_depth,
            cast(null as bigint) as peer_max_sync_bytes,
            cast(null as bytea) as public_key,
            cast(null as bigint) as received_message_id,
            cast(null as bytea) as received_running_hash,
            cast(null as bytea) as sent_running_hash,
            cast(null as bytea) as signature,
            cast(null as clpr_signature_scheme) as signature_scheme,
            'PENDING' as status,
            cast(null as int8range) as timestamp_range,
            cast(null as bytea) as trust_anchor,
            cast(null as bytea) as trust_anchor_id,
            cast(null as bigint) as verifier_contract_id,
            cast(null as bytea) as verifier_fingerprint
            """;

    /**
     * Returns channels together with open pending channel commitments, ordered by {@code created_timestamp}, which is
     * unique across both since they come from different transactions. Every column of both tables is selected; the
     * ones a table doesn't have are null, so a pending commitment has a null {@code channel_id} and a
     * {@code PENDING} status. The {@code 'OPEN'} literal is deliberate so the partial index on the pending table can
     * be used. Spring Data sorts and limits the outer query.
     */
    @Query(value = """
            select * from (
                select
            """ + CHANNEL_COLUMNS + """
                from clpr_channel
                where created_timestamp >= :lowerBound
                and created_timestamp <= :upperBound
                union all
                select
            """ + PENDING_COLUMNS + """
                from clpr_channel_pending_commitment
                where status = 'OPEN'
                and created_timestamp >= :lowerBound
                and created_timestamp <= :upperBound
            ) as u
            """, nativeQuery = true)
    List<ClprChannelData> findAllChannelsByCreatedTimestampBetween(long lowerBound, long upperBound, Pageable pageable);

    /** Returns the channel's current state, or null if there is none. */
    @Query(value = """
            select
            """ + CHANNEL_COLUMNS + """
            from clpr_channel
            where channel_id = :channelId
            """, nativeQuery = true)
    @Nullable
    ClprChannelData findByChannelId(byte[] channelId);

    /**
     * Returns the channel's state as of the given bounds, from the current or the history table: the latest version
     * whose {@code timestamp_range} overlaps {@code [lowerBound, upperBound]}, then filtered by {@code status} if
     * given. Both tables are selected through the same explicit column list, so their physical column order doesn't
     * matter, and a channel's timestamp ranges never overlap. Overlap is
     * expressed with explicit comparisons rather than {@code int8range(..., '[]')}, which overflows when the service
     * passes {@code Long.MAX_VALUE} as an open upper bound.
     */
    @Query(value = """
            select * from (
                select * from (
                    select
            """ + CHANNEL_COLUMNS + """
                    from clpr_channel
                    where channel_id = :channelId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                    union all
                    select
            """ + CHANNEL_COLUMNS + """
                    from clpr_channel_history
                    where channel_id = :channelId
                    and lower(timestamp_range) <= :upperBound
                    and (upper(timestamp_range) is null or upper(timestamp_range) > :lowerBound)
                ) as v
                order by v.timestamp_range desc
                limit 1
            ) as c
            where cast(:status as varchar) is null or c.status = cast(:status as varchar)
            """, nativeQuery = true)
    List<ClprChannelData> findByChannelIdAndTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, @Nullable String status);

    @Query(value = """
            select
            """ + CHANNEL_COLUMNS + """
            from clpr_channel
            where created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            and (cast(:status as clpr_channel_status) is null or status = cast(:status as clpr_channel_status))
            """, nativeQuery = true)
    List<ClprChannelData> findByCreatedTimestampBetween(
            long lowerBound, long upperBound, @Nullable String status, Pageable pageable);

    /**
     * Returns the open pending channel commitments, which are the ones not yet completed or abandoned. The
     * {@code 'OPEN'} literal is deliberate: a bind parameter may not match the predicate of the partial index
     * {@code clpr_channel_pending_commitment__created_timestamp}, which is defined {@code where status = 'OPEN'}. The
     * table isn't distributed in {@code v2}.
     */
    @Query(value = """
            select
            """ + PENDING_COLUMNS + """
            from clpr_channel_pending_commitment
            where status = 'OPEN'
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprChannelData> findPendingByCreatedTimestampBetween(long lowerBound, long upperBound, Pageable pageable);
}
