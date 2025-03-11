// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ImmutableAccount {
    FEE_COLLECTOR(98),
    ENTITY_STAKE(800);

    private final long num;

    public EntityId getScopedEntityId(CommonProperties commonProperties) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
