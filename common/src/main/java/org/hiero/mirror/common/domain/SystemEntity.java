// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class SystemEntity {

    private final CommonProperties commonProperties;

    @Getter(lazy = true)
    private final EntityId addressBookFile101 = toEntityId(101L);

    @Getter(lazy = true)
    private final EntityId addressBookFile102 = toEntityId(102L);

    @Getter(lazy = true)
    private final EntityId exchangeRateFile = toEntityId(112L);

    @Getter(lazy = true)
    private final EntityId feeCollectionAccount = toEntityId(802L);

    @Getter(lazy = true)
    private final EntityId feeScheduleFile = toEntityId(111L);

    @Getter(lazy = true)
    private final EntityId networkAdminFeeAccount = toEntityId(98L);

    @Getter(lazy = true)
    private final EntityId simpleFeeScheduleFile = toEntityId(113L);

    @Getter(lazy = true)
    private final EntityId hapiPermissionFile = toEntityId(122L);

    @Getter(lazy = true)
    private final EntityId networkPropertyFile = toEntityId(121L);

    @Getter(lazy = true)
    private final EntityId nodeRewardAccount = toEntityId(801L);

    @Getter(lazy = true)
    private final EntityId stakingRewardAccount = toEntityId(800L);

    @Getter(lazy = true)
    private final EntityId throttleDefinitionFile = toEntityId(123L);

    @Getter(lazy = true)
    private final EntityId treasuryAccount = toEntityId(2L);

    private EntityId toEntityId(long num) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
