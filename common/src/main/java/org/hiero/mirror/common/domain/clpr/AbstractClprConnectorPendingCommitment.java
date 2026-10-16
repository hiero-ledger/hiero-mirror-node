// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.google.common.collect.Range;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

/**
 * Unrevealed Connector commitment from the commit-reveal phase of {@code ClprRegisterConnector}/
 * {@code ClprCompleteConnector}. Keyed by {@code commitment} since {@code connector_id} isn't known until reveal.
 * {@code deleted} is reserved for schema symmetry; no handler sets it today. {@code skipPartialUpdate = true} since
 * completion never sets {@code createdTimestamp} itself.
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprConnectorPendingCommitment implements History {

    private Long completedTimestamp;

    @Id
    private byte[] commitment;

    private Long createdTimestamp;

    private Boolean deleted;

    @ToString.Exclude
    private byte[] publicKey;

    @ToString.Exclude
    private byte[] salt;

    @ToString.Exclude
    private byte[] signature;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprSignatureScheme signatureScheme;

    private Range<Long> timestampRange;
}
