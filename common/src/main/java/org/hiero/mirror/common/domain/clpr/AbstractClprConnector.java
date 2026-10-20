// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * On-ledger state for a single CLPR Connector, keyed by {@code (channelId, connectorId)}. Fields set only at
 * {@code ClprCompleteConnector} and never touched again are marked {@code updatable = false}; {@code lockedStake}
 * is also set there but stays updatable since {@code ClprSubmitBundle} slashing reduces it later.
 * {@code skipPartialUpdate = true} since {@code ClprDeregisterConnector} never sets {@code createdTimestamp}
 * itself. {@code publicKey}/{@code salt}/{@code signature}/{@code signatureScheme} are the one-time commit-reveal
 * completion proof, not real {@code ClprConnector} state.
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@IdClass(AbstractClprConnector.Id.class)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprConnector implements History {

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] adminKey;

    @jakarta.persistence.Id
    private byte[] channelId;

    @Column(updatable = false)
    @Convert(converter = EntityIdConverter.class)
    private EntityId connectorContractId;

    @jakarta.persistence.Id
    private byte[] connectorId;

    @Column(updatable = false)
    private Long createdTimestamp;

    private Boolean deleted;

    private Long inFlightMessageCount;

    // Mutable: ClprSubmitBundle slashing reduces this.
    private Long lockedStake;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] publicKey;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] salt;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] signature;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprSignatureScheme signatureScheme;

    private Long slashCount;

    @Convert(converter = EntityIdConverter.class)
    private EntityId stakeRecipientId;

    private Range<Long> timestampRange;

    @JsonIgnore
    public Id getId() {
        return new Id(channelId, connectorId);
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = 5327164893021746587L;

        private byte[] channelId;
        private byte[] connectorId;
    }
}
