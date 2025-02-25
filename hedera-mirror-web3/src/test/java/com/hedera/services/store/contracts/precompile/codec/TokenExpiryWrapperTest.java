// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static com.hedera.services.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenExpiryWrapperTest {

    private TokenExpiryWrapper wrapper;
    private static final AccountID payer = asAccount("0.0.12345");

    @BeforeEach
    void setup() {
        wrapper = createTokenExpiryWrapper();
    }

    @Test
    void autoRenewAccountIsCheckedAsExpected() {
        Assertions.assertEquals(payer, wrapper.autoRenewAccount());
        assertEquals(442L, wrapper.second());
        assertEquals(555L, wrapper.autoRenewPeriod());
        wrapper.setAutoRenewAccount(toGrpcAccountId(10));
        assertEquals(toGrpcAccountId(10), wrapper.autoRenewAccount());
    }

    @Test
    void objectContractWorks() {
        final var one = wrapper;
        final var two = createTokenExpiryWrapper();
        final var three = createTokenExpiryWrapper();
        three.setAutoRenewAccount(toGrpcAccountId(10));

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertEquals(one, two);
        assertNotEquals(one, three);

        assertNotEquals(one.hashCode(), three.hashCode());
        assertEquals(one.hashCode(), two.hashCode());

        assertEquals(
                "TokenExpiryWrapper{second=442, autoRenewAccount=accountNum: 12345\n" + ", autoRenewPeriod=555}",
                wrapper.toString());
    }

    static TokenExpiryWrapper createTokenExpiryWrapper() {
        return new TokenExpiryWrapper(442L, payer, 555L);
    }
}
