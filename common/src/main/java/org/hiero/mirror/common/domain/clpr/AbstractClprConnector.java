// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Embedded;

/**
 * On-ledger state for a single CLPR Connector, keyed by {@code (channelId, commitment)} since {@code connectorId}
 * isn't known until {@code ClprCompleteConnector} reveals it.
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprConnector implements History {

    @ToString.Exclude
    private byte[] adminKey;

    @Transient
    private byte[] channelId;

    @Transient
    private byte[] commitment;

    private EntityId connectorContractId;

    // Null until ClprCompleteConnector reveals it.
    private byte[] connectorId;

    // Reset fresh by every ClprCompleteConnector, including reactivation after deregistration -- not the row's
    // original registration time.
    private Long createdTimestamp;

    private Long inFlightMessageCount;

    // Mutable: ClprSubmitBundle slashing reduces this.
    private Long lockedStake;

    // Not updatable=false: null at registration, set once by ClprCompleteConnector -- on an UPDATE of the existing
    // row, not an INSERT, since the row already exists from registration.
    @ToString.Exclude
    private byte[] publicKey;

    @ToString.Exclude
    private byte[] salt;

    @ToString.Exclude
    private byte[] signature;

    // Raw com.hederahashgraph.api.proto.java.ClprSignatureScheme wire number -- preserved as-is even if this mirror
    // node doesn't recognize it yet, rather than losing a value a newer consensus node sent.
    private Integer signatureScheme;

    private Long slashCount;

    private EntityId stakeRecipientId;

    private ClprConnectorStatus status;

    private Range<Long> timestampRange;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    public Id getId() {
        return new Id(channelId, commitment);
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.channelId = id.getChannelId();
        this.commitment = id.getCommitment();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 5327164893021746587L;

        private byte[] channelId;
        private byte[] commitment;
    }
}
