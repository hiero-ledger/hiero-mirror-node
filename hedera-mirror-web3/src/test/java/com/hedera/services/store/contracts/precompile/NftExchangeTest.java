// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.A;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.B;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.NON_FUNGIBLE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NftExchangeTest {

    @Test
    void createsExpectedCryptoTransfer() {
        final var nftExchange = new NftExchange(1L, NON_FUNGIBLE, A, B);
        assertFalse(nftExchange.isApproval());
        assertEquals(NON_FUNGIBLE, nftExchange.getTokenType());
        assertTrue(nftExchange.asGrpc().hasSenderAccountID());
        assertEquals(1L, nftExchange.getSerialNo());
        assertTrue(NftExchange.fromApproval(1L, NON_FUNGIBLE, A, B).isApproval());
    }
}
