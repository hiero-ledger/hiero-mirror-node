// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import com.hedera.mirror.common.CommonProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SystemEntity {
    FEE_COLLECTOR(98),
    NODE_REWARD_ACCOUNT(801),
    STAKING_REWARD_ACCOUNT(800),
    TREASURY_ACCOUNT(2);

    private final long num;

    public EntityId getScopedEntityId(CommonProperties commonProperties) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
