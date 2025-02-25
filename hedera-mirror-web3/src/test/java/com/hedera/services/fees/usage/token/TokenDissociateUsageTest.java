// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenDissociateUsageTest {

    private TransactionBody txn;

    private TxnUsageEstimator base;

    @BeforeEach
    void setup() {
        base = mock(TxnUsageEstimator.class);
    }

    @Test
    void assertSelf() {
        final var subject = TokenDissociateUsage.newEstimate(txn, base);
        assertEquals(subject, subject.self());
    }
}
