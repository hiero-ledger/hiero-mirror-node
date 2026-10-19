// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code custom_fee.royalty_fees} (JSONB). A bare {@code List} is mapped by Spring
 * Data JDBC as a separate aggregate table.
 */
public record RoyaltyFeesHolder(List<RoyaltyFee> items) implements ListHolder<RoyaltyFee> {

    public static RoyaltyFeesHolder of(List<RoyaltyFee> list) {
        if (list == null) {
            return null;
        }
        return new RoyaltyFeesHolder(List.copyOf(list));
    }
}
