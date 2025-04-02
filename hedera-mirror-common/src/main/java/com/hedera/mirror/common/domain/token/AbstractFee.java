// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.token;

import com.hedera.mirror.common.domain.entity.EntityId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class AbstractFee {

    /**
     * When true, fee collectors do not pay the fixed fee if they are involved in a transaction.
     * When false, fee collectors must still pay the fixed fee, just like any other account.
     */
    private boolean allCollectorsAreExempt;

    /**
     * The id of the account collecting the fee.
     */
    private EntityId collectorAccountId;

    public abstract boolean isChargedInToken(EntityId tokenId);
}
