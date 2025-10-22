// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.web3.ContextExtension;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class HederaBlockHashOperationTest {

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private EVM evm;

    @Mock
    private GasCalculator gasCalculator;

    private HederaBlockHashOperation subject;

    @BeforeEach
    void setup() {
        subject = new HederaBlockHashOperation(gasCalculator);
    }

    @Test
    void testTooLargeValuePassedReturnsZero() {
        given(messageFrame.popStackItem()).willReturn(Bytes.of(0xa, 0xb, 0xa, 0xb, 0xa, 0xb, 0xa, 0xb, 0xa));

        subject.execute(messageFrame, evm);

        verify(messageFrame, times(1)).pushStackItem(UInt256.ZERO);
    }
}
