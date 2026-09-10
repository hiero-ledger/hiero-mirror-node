// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.tss;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code ledger.node_contributions} (JSONB). A bare {@code List} is treated by Spring Data JDBC
 * as a separate aggregate table.
 */
public record LedgerNodeContributionListHolder(List<LedgerNodeContribution> items) implements Serializable {

    public static LedgerNodeContributionListHolder of(List<LedgerNodeContribution> list) {
        if (list == null) {
            return null;
        }
        return new LedgerNodeContributionListHolder(List.copyOf(list));
    }
}
