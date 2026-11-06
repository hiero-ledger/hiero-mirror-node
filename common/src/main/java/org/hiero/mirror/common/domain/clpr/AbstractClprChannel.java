// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * On-ledger state for a single CLPR Channel, written once {@code channel_id} is known (see
 * {@link ClprChannelPendingCommitment} for the commit-only phase).
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprChannel implements History {

    private Long ackedMessageId;

    @Column(updatable = false)
    private String chainId;

    @Column(updatable = false)
    private byte[] channelContext;

    @Id
    @ToString.Include
    private byte[] channelId;

    @Column(updatable = false)
    private byte[] configProofBytes;

    @Column(updatable = false)
    @ToString.Include
    private Long createdTimestamp;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ClprEndpoint> endpointManifest;

    @Column(updatable = false)
    private byte[] endpointManifestProofBytes;

    private Long endpointManifestVersion;

    private Long lastConfigTimestamp;

    private Long nextMessageId;

    @Column(updatable = false)
    private byte[] ownershipCommitment;

    private Long peerConfigTimestamp;

    private Long peerMaxGasPerMessage;

    private Long peerMaxLocalEndpoints;

    private Long peerMaxMessagePayloadBytes;

    private Long peerMaxMessagesPerBundle;

    private Long peerMaxPeerEndpoints;

    private Long peerMaxQueueDepth;

    private Long peerMaxSyncBytes;

    @Column(updatable = false)
    private byte[] publicKey;

    private Long receivedMessageId;

    private byte[] receivedRunningHash;

    private byte[] sentRunningHash;

    @Column(updatable = false)
    private byte[] signature;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprSignatureScheme signatureScheme;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @ToString.Include
    private ClprChannelStatus status;

    @ToString.Include
    private Range<Long> timestampRange;

    private byte[] trustAnchor;

    private byte[] trustAnchorId;

    @Column(updatable = false)
    @Convert(converter = EntityIdConverter.class)
    private EntityId verifierContractId;

    @Column(updatable = false)
    private byte[] verifierFingerprint;
}
