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
import org.hiero.mirror.common.domain.Upsertable;

/**
 * Unrevealed Channel commitment from the commit-reveal phase of {@code ClprRegisterChannel}/
 * {@code ClprCompleteChannel}. Keyed by {@code ownership_commitment} since {@code channel_id} isn't known until
 * reveal. {@code skipPartialUpdate = true} since completion never sets {@code createdTimestamp} itself.
 */
@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprChannelPendingCommitment implements ClprPendingCommitment {

    private Long createdTimestamp;

    @Id
    private byte[] ownershipCommitment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClprPendingCommitmentStatus status;

    private Range<Long> timestampRange;
}
