// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code custom_fee.fixed_fees} (JSONB). A bare {@code List} is mapped by Spring Data
 * JDBC as a separate aggregate table.
 */
public record FixedFeesHolder(List<FixedFee> items) implements Serializable {

    public static FixedFeesHolder of(List<FixedFee> list) {
        if (list == null) {
            return null;
        }
        return new FixedFeesHolder(List.copyOf(list));
    }
}
