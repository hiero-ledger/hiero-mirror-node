// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.A;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.B;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.SECOND_AMOUNT;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HbarTransferTest {

    @Test
    void createsExpectedCryptoTransfer() {
        final var hbarTransfer = new HbarTransfer(SECOND_AMOUNT, false, B, A);
        assertFalse(hbarTransfer.isApproval());
        assertEquals(B, hbarTransfer.sender());
        assertEquals(A, hbarTransfer.receiver());
        assertEquals(200, hbarTransfer.amount());
        assertEquals(200, hbarTransfer.receiverAdjustment().getAmount());
        assertEquals(-200, hbarTransfer.senderAdjustment().getAmount());
    }
}
