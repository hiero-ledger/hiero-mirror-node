// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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
 * On-ledger state for a single CLPR Channel, created in ACTIVE status by a single {@code ClprCreateChannel}
 * transaction.
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

    private Long peerConfigTimestamp;

    @AttributeOverrides({
        @AttributeOverride(name = "maxGasPerMessage", column = @Column(name = "peer_max_gas_per_message")),
        @AttributeOverride(name = "maxLocalEndpoints", column = @Column(name = "peer_max_local_endpoints")),
        @AttributeOverride(name = "maxMessagePayloadBytes", column = @Column(name = "peer_max_message_payload_bytes")),
        @AttributeOverride(name = "maxMessagesPerBundle", column = @Column(name = "peer_max_messages_per_bundle")),
        @AttributeOverride(name = "maxPeerEndpoints", column = @Column(name = "peer_max_peer_endpoints")),
        @AttributeOverride(name = "maxQueueDepth", column = @Column(name = "peer_max_queue_depth")),
        @AttributeOverride(name = "maxSyncBytes", column = @Column(name = "peer_max_sync_bytes"))
    })
    @Embedded
    @JsonUnwrapped(prefix = "peer_")
    private ClprThrottle peerThrottle;

    @Column(updatable = false)
    private byte[] peerVerifier;

    private Long receivedMessageId;

    private byte[] receivedRunningHash;

    @Column(updatable = false)
    private byte[] salt;

    private byte[] sentRunningHash;

    // Raw com.hederahashgraph.api.proto.java.ClprChannelStatus wire number -- preserved as-is even if this mirror
    // node doesn't recognize it yet, rather than losing a value a newer consensus node sent.
    @ToString.Include
    private Integer status;

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
