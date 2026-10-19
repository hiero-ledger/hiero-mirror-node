// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code custom_fee.fixed_fees} (JSONB). A bare {@code List} is mapped by Spring Data
 * JDBC as a separate aggregate table.
 */
public record FixedFeesHolder(List<FixedFee> items) implements ListHolder<FixedFee> {

    public static FixedFeesHolder of(List<FixedFee> list) {
        if (list == null) {
            return null;
        }
        return new FixedFeesHolder(List.copyOf(list));
    }
}
