// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.contracts.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaV22Test {
    GasCalculatorHederaV22 subject;

    @Mock
    MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    UsagePricesProvider usagePricesProvider;

    @Mock
    HbarCentExchange hbarCentExchange;

    @BeforeEach
    void setUp() {
        subject = new GasCalculatorHederaV22(usagePricesProvider, hbarCentExchange);
    }

    @Test
    void gasDepositCost() {
        assertThat(subject.codeDepositGasCost(37)).isGreaterThan(0L);
    }

    @Test
    void transactionIntrinsicGasCost() {
        assertEquals(
                4 * 2 + // zero byte cost
                        16 * 3 + // non-zero byte cost
                        21_000L, // base TX cost
                subject.transactionIntrinsicGasCost(Bytes.of(0, 1, 2, 3, 0), false));
        assertEquals(
                4 * 3 + // zero byte cost
                        16 * 2 + // non-zero byte cost
                        21_000L + // base TX cost
                        32_000L, // contract creation base cost
                subject.transactionIntrinsicGasCost(Bytes.of(0, 1, 0, 3, 0), true));
    }
}
