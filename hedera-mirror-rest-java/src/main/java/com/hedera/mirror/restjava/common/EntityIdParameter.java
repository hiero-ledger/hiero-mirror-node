// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import org.apache.commons.lang3.StringUtils;

public sealed interface EntityIdParameter
        permits EntityIdNumParameter, EntityIdEvmAddressParameter, EntityIdAliasParameter {

    static EntityIdParameter valueOf(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Missing or empty ID");
        }

        EntityIdParameter entityId;

        if ((entityId = EntityIdNumParameter.valueOf(id)) != null) {
            return entityId;
        } else if ((entityId = EntityIdEvmAddressParameter.valueOf(id)) != null) {
            return entityId;
        } else if ((entityId = EntityIdAliasParameter.valueOf(id)) != null) {
            return entityId;
        } else {
            throw new IllegalArgumentException("Unsupported ID format: %s".formatted(id));
        }
    }

    long shard();

    long realm();
}
