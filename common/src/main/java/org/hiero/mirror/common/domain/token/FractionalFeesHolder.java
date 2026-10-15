// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.token;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code custom_fee.fractional_fees} (JSONB). A bare {@code List} is mapped by Spring
 * Data JDBC as a separate aggregate table.
 */
public record FractionalFeesHolder(List<FractionalFee> items) implements ListHolder<FractionalFee> {

    public static FractionalFeesHolder of(List<FractionalFee> list) {
        if (list == null) {
            return null;
        }
        return new FractionalFeesHolder(List.copyOf(list));
    }
}
