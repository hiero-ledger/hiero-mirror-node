// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class SystemEntities {

    private final CommonProperties commonProperties;

    @Getter(lazy = true)
    private final EntityId addressBookFile101 = toEntityId(101L);

    @Getter(lazy = true)
    private final EntityId addressBookFile102 = toEntityId(102L);

    @Getter(lazy = true)
    private final EntityId feeCollectorAccount = toEntityId(98L);

    @Getter(lazy = true)
    private final EntityId nodeRewardAccount = toEntityId(801L);

    @Getter(lazy = true)
    private final EntityId stakingRewardAccount = toEntityId(800L);

    @Getter(lazy = true)
    private final EntityId treasuryAccount = toEntityId(2L);

    private EntityId toEntityId(long num) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
