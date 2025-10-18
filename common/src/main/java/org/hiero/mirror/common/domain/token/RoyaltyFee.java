// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * Royalty fee is applied every time a NFT is transferred(changes ownership). It is a fraction of the amount exchanged
 * for the NFT. If no value is exchanged for the NFT a fallback amount is deducted from the receiver.
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class RoyaltyFee extends AbstractFee {

    /**
     * Fee that is going to be deducted from the NFT receiver if no value is exchanged for the NFT.
     */
    private FallbackFee fallbackFee;

    /**
     * Denominator of the fraction of the value exchanged for the NFT.
     */
    private long denominator;

    /**
     * Numerator of the fraction of the value exchanged for the NFT.
     */
    private long numerator;

    @Override
    public boolean isChargedInToken(EntityId tokenId) {
        return false;
    }
}
