// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.google.common.collect.Range;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;

/**
 * Unrevealed Channel commitment from the commit-reveal phase of {@code ClprRegisterChannel}/
 * {@code ClprCompleteChannel}. Keyed by {@code ownership_commitment} since {@code channel_id} isn't known until
 * reveal.
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true, skipPartialUpdate = true)
public abstract class AbstractClprChannelPendingCommitment implements History {

    private Long createdTimestamp;

    @Id
    private byte[] ownershipCommitment;

    private ClprPendingCommitmentStatus status;

    private Range<Long> timestampRange;
}
