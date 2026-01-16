// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.google.common.collect.Range;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntityStake implements History {

    // The epoch day of the period for which the pending reward has included so far
    private long endStakePeriod;

    @Id
    private Long id;

    private long pendingReward;

    // The *Start properties are the entity's staking state at the beginning of the endStakePeriod + 1 staking period
    private long stakedNodeIdStart;

    private long stakedToMe;

    private long stakeTotalStart;

    private Range<Long> timestampRange;
}
