// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import com.google.common.collect.Range;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntityStake implements History {

    private long endStakePeriod;

    @Id
    private Long id;

    private long pendingReward;

    private long stakedNodeIdStart;

    private long stakedToMe;

    private long stakeTotalStart;

    private Range<Long> timestampRange;
}
