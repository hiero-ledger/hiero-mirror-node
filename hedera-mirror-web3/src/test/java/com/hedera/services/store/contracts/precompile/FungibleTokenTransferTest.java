// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

class FungibleTokenTransferTest {

    static final long SECOND_AMOUNT = 200;
    static final AccountID A = asAccount("0.0.2");
    static final AccountID B = asAccount("0.0.3");
    static final TokenID FUNGIBLE = asToken("0.0.555");
    static final TokenID NON_FUNGIBLE = asToken("0.0.666");

    @Test
    void createsExpectedCryptoTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, B, A);
        assertEquals(FUNGIBLE, fungibleTransfer.getDenomination());
    }
}
