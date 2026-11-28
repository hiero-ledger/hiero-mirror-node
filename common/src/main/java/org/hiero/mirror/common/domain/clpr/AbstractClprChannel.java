// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;

/**
 * On-ledger state for a single CLPR Channel, written once {@code channel_id} is known (see
 * {@link ClprChannelPendingCommitment} for the commit-only phase).
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprChannel implements History {

    private Long ackedMessageId;

    @InsertOnlyProperty
    private String chainId;

    @InsertOnlyProperty
    private byte[] channelContext;

    @Id
    @ToString.Include
    private byte[] channelId;

    @InsertOnlyProperty
    private byte[] configProofBytes;

    @InsertOnlyProperty
    @ToString.Include
    private Long createdTimestamp;

    @Column("endpoint_manifest")
    @JsonIgnore
    private ClprEndpointListHolder endpointManifestColumn;

    @InsertOnlyProperty
    private byte[] endpointManifestProofBytes;

    private Long endpointManifestVersion;

    private Long lastConfigTimestamp;

    private Long nextMessageId;

    @InsertOnlyProperty
    private byte[] ownershipCommitment;

    private Long peerConfigTimestamp;

    @Embedded.Nullable(prefix = "peer_")
    @JsonUnwrapped(prefix = "peer_")
    private ClprThrottle peerThrottle;

    @InsertOnlyProperty
    private byte[] publicKey;

    private Long receivedMessageId;

    private byte[] receivedRunningHash;

    private byte[] sentRunningHash;

    @InsertOnlyProperty
    private byte[] signature;

    // Raw com.hederahashgraph.api.proto.java.ClprSignatureScheme wire number -- preserved as-is even if this mirror
    // node doesn't recognize it yet, rather than losing a value a newer consensus node sent.
    @InsertOnlyProperty
    private Integer signatureScheme;

    // Raw com.hederahashgraph.api.proto.java.ClprChannelStatus wire number, same reasoning as signatureScheme above.
    @ToString.Include
    private Integer status;

    @ToString.Include
    private Range<Long> timestampRange;

    private byte[] trustAnchor;

    private byte[] trustAnchorId;

    @InsertOnlyProperty
    private EntityId verifierContractId;

    @InsertOnlyProperty
    private byte[] verifierFingerprint;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<ClprEndpoint> getEndpointManifest() {
        return endpointManifestColumn == null ? null : endpointManifestColumn.items();
    }

    public void setEndpointManifest(List<ClprEndpoint> endpointManifest) {
        this.endpointManifestColumn = ClprEndpointListHolder.of(endpointManifest);
    }

    public abstract static class AbstractClprChannelBuilder<
            C extends AbstractClprChannel, B extends AbstractClprChannelBuilder<C, B>> {

        public B endpointManifest(List<ClprEndpoint> endpointManifest) {
            this.endpointManifestColumn = ClprEndpointListHolder.of(endpointManifest);
            return self();
        }
    }
}
