// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.google.common.collect.Range;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * Read-only row of the union of {@code clpr_channel} and open {@code clpr_channel_pending_commitment}, with every
 * column of both tables. Columns a table doesn't have are null, so a pending commitment has only
 * {@code createdTimestamp}, {@code ownershipCommitment} and {@code status}. It's only
 * populated by a native query, so there's no backing table. {@code ownershipCommitment} is unique across both tables,
 * so it's the id. {@code status} is text since it also holds {@code PENDING}.
 */
@AllArgsConstructor
@Builder
@Data
@Entity
@Immutable
@NoArgsConstructor
public class ClprChannelData {

    private Long ackedMessageId;

    private String chainId;

    private byte[] channelContext;

    private byte[] channelId;

    private byte[] configProofBytes;

    private Long createdTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ClprEndpoint> endpointManifest;

    private byte[] endpointManifestProofBytes;

    private Long endpointManifestVersion;

    private Long lastConfigTimestamp;

    private Long nextMessageId;

    @Id
    private byte[] ownershipCommitment;

    private Long peerConfigTimestamp;

    private Long peerMaxGasPerMessage;

    private Long peerMaxLocalEndpoints;

    private Long peerMaxMessagePayloadBytes;

    private Long peerMaxMessagesPerBundle;

    private Long peerMaxPeerEndpoints;

    private Long peerMaxQueueDepth;

    private Long peerMaxSyncBytes;

    private byte[] publicKey;

    private Long receivedMessageId;

    private byte[] receivedRunningHash;

    private byte[] sentRunningHash;

    private byte[] signature;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprSignatureScheme signatureScheme;

    private String status;

    private Range<Long> timestampRange;

    private byte[] trustAnchor;

    private byte[] trustAnchorId;

    @Convert(converter = EntityIdConverter.class)
    private EntityId verifierContractId;

    private byte[] verifierFingerprint;
}
