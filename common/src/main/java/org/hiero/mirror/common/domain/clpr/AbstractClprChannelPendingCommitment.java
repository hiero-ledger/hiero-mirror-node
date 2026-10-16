// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.google.common.collect.Range;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
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
public abstract class AbstractClprChannelPendingCommitment implements History {

    private Long completedTimestamp;

    private Long createdTimestamp;

    private Boolean deleted;

    @Id
    private byte[] ownershipCommitment;

    private Range<Long> timestampRange;
}
