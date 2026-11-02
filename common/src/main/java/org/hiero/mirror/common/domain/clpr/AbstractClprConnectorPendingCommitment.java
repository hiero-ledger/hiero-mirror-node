// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.google.common.collect.Range;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

/**
 * Unrevealed Connector commitment from the commit-reveal phase of {@code ClprRegisterConnector}/
 * {@code ClprCompleteConnector}. Keyed by {@code commitment} since {@code connector_id} isn't known until reveal.
 * {@code DELETED} is reserved for schema symmetry with the Channel side; no handler sets it today -- there's no
 * abandonment path for a connector commitment. {@code skipPartialUpdate = true} since completion never sets
 * {@code createdTimestamp} itself.
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprConnectorPendingCommitment implements History {

    @Id
    private byte[] commitment;

    private Long createdTimestamp;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprPendingCommitmentStatus status;

    private Range<Long> timestampRange;
}
