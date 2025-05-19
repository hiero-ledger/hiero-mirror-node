// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.ledger.BalanceChange;
import java.util.List;

public class ImpliedTransfers {

    private final List<BalanceChange> changes;

    public ImpliedTransfers(List<BalanceChange> changes) {
        this.changes = changes;
    }

    public List<BalanceChange> getAllBalanceChanges() {
        return changes;
    }
}
