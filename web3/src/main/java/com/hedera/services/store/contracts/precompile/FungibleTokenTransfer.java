// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

public class FungibleTokenTransfer extends HbarTransfer {

    private final TokenID denomination;

    public FungibleTokenTransfer(
            final long amount,
            final boolean isApproval,
            final TokenID denomination,
            final AccountID sender,
            final AccountID receiver) {
        super(amount, isApproval, sender, receiver);
        this.denomination = denomination;
    }

    public TokenID getDenomination() {
        return denomination;
    }
}
