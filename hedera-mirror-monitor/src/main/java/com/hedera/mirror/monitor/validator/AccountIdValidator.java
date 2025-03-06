// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.validator;

import static com.hedera.mirror.monitor.OperatorProperties.DEFAULT_OPERATOR_ACCOUNT_ID;

import com.hedera.mirror.common.domain.entity.EntityId;

public record AccountIdValidator(long shard, long realm) {

    /**
     * Validates that the accountId has matching shard and realm. If the accountId is the default operator account id
     * "0.0.2", its shard and realm will get corrected and the corrected account id returned.
     *
     * @param accountId - The accountId to validate
     * @return accountId with shard and realm matching the network if it's the default operator account id
     */
    public String validate(String accountId) {
        var entityId = EntityId.of(accountId);
        if (entityId.getShard() != shard || entityId.getRealm() != realm) {
            if (DEFAULT_OPERATOR_ACCOUNT_ID.equals(accountId)) {
                return EntityId.of(shard, realm, entityId.getNum()).toString();
            } else {
                throw new IllegalArgumentException(
                        "Account ID %s should have shard=%d and realm=%d".formatted(accountId, shard, realm));
            }
        }

        return accountId;
    }
}
