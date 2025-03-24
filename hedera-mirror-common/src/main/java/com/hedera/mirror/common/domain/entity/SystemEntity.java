// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import com.hedera.mirror.common.CommonProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SystemEntity {
    ADDRESS_BOOK_101(101L),
    ADDRESS_BOOK_102(102L),
    EXCHANGE_RATE(112L),
    FEE_COLLECTOR_ACCOUNT(98L),
    FEE_SCHEDULE(111L),
    HAPI_PERMISSION(122L),
    NETWORK_PROPERTY(121L),
    NODE_REWARD_ACCOUNT(801L),
    STAKING_REWARD_ACCOUNT(800L),
    THROTTLE_DEFINITION(123L),
    TREASURY_ACCOUNT(2L);

    private final long num;

    public EntityId getScopedEntityId(CommonProperties commonProperties) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
