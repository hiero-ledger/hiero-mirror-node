// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code transaction.itemized_transfer} (JSONB). A bare {@code List} is treated by Spring Data JDBC
 * as a separate aggregate table.
 */
public record ItemizedTransferListHolder(List<ItemizedTransfer> items) implements Serializable {

    public static ItemizedTransferListHolder of(List<ItemizedTransfer> list) {
        if (list == null) {
            return null;
        }
        return new ItemizedTransferListHolder(List.copyOf(list));
    }
}
