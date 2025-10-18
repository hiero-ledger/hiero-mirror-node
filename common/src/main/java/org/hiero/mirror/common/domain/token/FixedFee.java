// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FixedFee extends AbstractFee {

    /**
     * The amount transferred to the collector account when a token is transferred.
     */
    private long amount;

    /**
     * Fungible token the fee is paid in, if left unset - paid in HBAR.
     */
    private EntityId denominatingTokenId;

    @Override
    public boolean isChargedInToken(EntityId tokenId) {
        return tokenId.equals(denominatingTokenId);
    }
}
