// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;
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
 * On-ledger state for a single CLPR Connector, keyed by {@code (channelId, commitment)} since {@code connectorId}
 * isn't known until {@code ClprCompleteConnector} reveals it.
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@IdClass(AbstractClprConnector.Id.class)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprConnector implements History {

    @ToString.Exclude
    private byte[] adminKey;

    @jakarta.persistence.Id
    private byte[] channelId;

    @jakarta.persistence.Id
    private byte[] commitment;

    @Convert(converter = EntityIdConverter.class)
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprSignatureScheme signatureScheme;

    private Long slashCount;

    @Convert(converter = EntityIdConverter.class)
    private EntityId stakeRecipientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprConnectorStatus status;

    private Range<Long> timestampRange;

    @JsonIgnore
    public Id getId() {
        return new Id(channelId, commitment);
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
