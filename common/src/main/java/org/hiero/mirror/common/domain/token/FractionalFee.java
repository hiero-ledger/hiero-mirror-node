// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FractionalFee extends AbstractFee {

    private long denominator;

    private Long maximumAmount;

    private long minimumAmount;

    private long numerator;

    private boolean netOfTransfers;

    @Override
    public boolean isChargedInToken(EntityId tokenId) {
        return true;
    }
}
