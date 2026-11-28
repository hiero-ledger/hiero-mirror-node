// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Every method returns {@link ClprChannelDto}, whose {@code status} also holds {@code PENDING}, so the queries
 * select every column. The status and signature scheme are the stored proto wire numbers. Queries filtered by
 * {@code channel_id}, the distribution column in {@code v2}, route to a single shard. The others scatter-gather
 * across all shards. {@code created_timestamp} is unique per channel, so it's the sole sort key. All bounds are
 * inclusive and computed by the service.
 */
public interface ClprChannelRepository extends Repository<ClprChannel, byte[]> {

    /** The columns of {@code clpr_channel} as {@link ClprChannelDto}, for use as a select list. */
    String CHANNEL_COLUMNS = """
            acked_message_id as ackedMessageId,
            chain_id as chainId,
            channel_context as channelContext,
            channel_id as channelId,
            config_proof_bytes as configProofBytes,
            created_timestamp as createdTimestamp,
            cast(endpoint_manifest as text) as endpointManifest,
            endpoint_manifest_proof_bytes as endpointManifestProofBytes,
            endpoint_manifest_version as endpointManifestVersion,
            last_config_timestamp as lastConfigTimestamp,
            next_message_id as nextMessageId,
            ownership_commitment as ownershipCommitment,
            peer_config_timestamp as peerConfigTimestamp,
            peer_max_gas_per_message as peerMaxGasPerMessage,
            peer_max_local_endpoints as peerMaxLocalEndpoints,
            peer_max_message_payload_bytes as peerMaxMessagePayloadBytes,
            peer_max_messages_per_bundle as peerMaxMessagesPerBundle,
            peer_max_peer_endpoints as peerMaxPeerEndpoints,
            peer_max_queue_depth as peerMaxQueueDepth,
            peer_max_sync_bytes as peerMaxSyncBytes,
            public_key as publicKey,
            received_message_id as receivedMessageId,
            received_running_hash as receivedRunningHash,
            sent_running_hash as sentRunningHash,
            signature as signature,
            cast(signature_scheme as integer) as signatureScheme,
            cast(status as integer) as status,
            lower(timestamp_range) as timestampLower,
            upper(timestamp_range) as timestampUpper,
            trust_anchor as trustAnchor,
            trust_anchor_id as trustAnchorId,
            verifier_contract_id as verifierContractId,
            verifier_fingerprint as verifierFingerprint
            """;

    /**
     * The columns of {@code clpr_channel_pending_commitment} as {@link ClprChannelDto}, for use as a select list. The
     * columns the table doesn't have, including {@code timestamp_range}, are null and {@code status} is {@code PENDING}.
     */
    String PENDING_COLUMNS = """
            cast(null as bigint) as ackedMessageId,
            cast(null as varchar) as chainId,
            cast(null as bytea) as channelContext,
            cast(null as bytea) as channelId,
            cast(null as bytea) as configProofBytes,
            created_timestamp as createdTimestamp,
            cast(null as text) as endpointManifest,
            cast(null as bytea) as endpointManifestProofBytes,
            cast(null as bigint) as endpointManifestVersion,
            cast(null as bigint) as lastConfigTimestamp,
            cast(null as bigint) as nextMessageId,
            ownership_commitment as ownershipCommitment,
            cast(null as bigint) as peerConfigTimestamp,
            cast(null as bigint) as peerMaxGasPerMessage,
            cast(null as bigint) as peerMaxLocalEndpoints,
            cast(null as bigint) as peerMaxMessagePayloadBytes,
            cast(null as bigint) as peerMaxMessagesPerBundle,
            cast(null as bigint) as peerMaxPeerEndpoints,
            cast(null as bigint) as peerMaxQueueDepth,
            cast(null as bigint) as peerMaxSyncBytes,
            cast(null as bytea) as publicKey,
            cast(null as bigint) as receivedMessageId,
            cast(null as bytea) as receivedRunningHash,
            cast(null as bytea) as sentRunningHash,
            cast(null as bytea) as signature,
            cast(null as integer) as signatureScheme,
            cast(0 as integer) as status,
            cast(null as bigint) as timestampLower,
            cast(null as bigint) as timestampUpper,
            cast(null as bytea) as trustAnchor,
            cast(null as bytea) as trustAnchorId,
            cast(null as bigint) as verifierContractId,
            cast(null as bytea) as verifierFingerprint
            """;

    /**
     * Channels together with open pending channel commitments, which come from different transactions, so
     * {@code created_timestamp} is unique across both. Every column of both tables is selected; the ones a table
     * doesn't have are null, so a pending commitment has a null {@code channel_id} and a {@code PENDING} status, which
     * is what makes {@code status = PENDING} select only pending commitments. The {@code 'OPEN'} literal is deliberate
     * so the partial index on the pending table can be used.
     */
    String SELECT_ALL_CHANNELS = """
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
            where cast(:status as integer) is null or u.status = cast(:status as integer)
            """;

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
                order by v.timestampLower desc
                limit 1
            ) as c
            where cast(:status as integer) is null or c.status = cast(:status as integer)
            """, nativeQuery = true)
    List<ClprChannelDto> findByChannelIdAndTimestampBetween(
            byte[] channelId, long lowerBound, long upperBound, @Nullable Integer status);

    @Query(value = SELECT_ALL_CHANNELS + "order by u.createdTimestamp asc limit :limit", nativeQuery = true)
    List<ClprChannelDto> findAllAsc(long lowerBound, long upperBound, @Nullable Integer status, int limit);

    @Query(value = SELECT_ALL_CHANNELS + "order by u.createdTimestamp desc limit :limit", nativeQuery = true)
    List<ClprChannelDto> findAllDesc(long lowerBound, long upperBound, @Nullable Integer status, int limit);

    default List<ClprChannelDto> findAllByCreatedTimestampBetween(
            long lowerBound, long upperBound, @Nullable Integer status, Direction order, int limit) {
        return order.isAscending()
                ? findAllAsc(lowerBound, upperBound, status, limit)
                : findAllDesc(lowerBound, upperBound, status, limit);
    }
}
