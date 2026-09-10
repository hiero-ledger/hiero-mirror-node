// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.io.Serializable;
import java.util.List;
import org.hiero.mirror.common.domain.token.NftTransfer;

/**
 * Single-valued JDBC column type for {@code transaction.nft_transfer} (JSONB). A bare {@code List} is treated by Spring Data JDBC
 * as a separate aggregate table.
 */
public record NftTransferListHolder(List<NftTransfer> items) implements Serializable {

    public static NftTransferListHolder of(List<NftTransfer> list) {
        if (list == null) {
            return null;
        }
        return new NftTransferListHolder(List.copyOf(list));
    }
}
