// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FallbackFee {

    /**
     * The amount that is going to be paid by the receiver if no value is exchanged for the NFT.
     */
    private long amount;

    /**
     * Fungible token the fee is paid in, if left unset - paid in HBAR.
     */
    private EntityId denominatingTokenId;
}
