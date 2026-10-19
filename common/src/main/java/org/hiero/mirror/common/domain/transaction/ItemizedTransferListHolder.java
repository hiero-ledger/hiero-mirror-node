// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code transaction.itemized_transfer} (JSONB). A bare {@code List} is treated by Spring Data JDBC
 * as a separate aggregate table.
 */
public record ItemizedTransferListHolder(List<ItemizedTransfer> items) implements ListHolder<ItemizedTransfer> {

    public static ItemizedTransferListHolder of(List<ItemizedTransfer> list) {
        if (list == null) {
            return null;
        }
        return new ItemizedTransferListHolder(List.copyOf(list));
    }
}
