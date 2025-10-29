// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.contracts.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaV19Test {
    GasCalculatorHederaV19 subject;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private UsagePricesProvider usagePricesProvider;

    @Mock
    private HbarCentExchange hbarCentExchange;

    @Mock
    private MessageFrame messageFrame;

    @BeforeEach
    void setUp() {
        subject = new GasCalculatorHederaV19(usagePricesProvider, hbarCentExchange);
    }

    @Test
    void gasDepositCost() {
        assertThat(subject.codeDepositGasCost(1)).isGreaterThan(0L);
    }

    @Test
    void transactionIntrinsicGasCost() {
        assertEquals(0L, subject.transactionIntrinsicGasCost(Bytes.of(1, 2, 3), true, 0L));
    }

    @Test
    void logOperationGasCost() {
        final var consensusTime = 123L;
        final var functionality = HederaFunctionality.ContractCreate;
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        final var returningDeque = new ArrayDeque<MessageFrame>() {};
        returningDeque.add(messageFrame);

        final var rbh = 20000L;
        final var feeComponents = FeeComponents.newBuilder().setRbh(rbh);
        final var feeData = FeeData.newBuilder().setServicedata(feeComponents).build();
        final var blockConsTime = Instant.ofEpochSecond(consensusTime);
        final var blockNo = 123L;

        given(messageFrame.getGasPrice()).willReturn(Wei.of(2000L));
        given(messageFrame.getBlockValues()).willReturn(new HederaBlockValues(10L, blockNo, blockConsTime));
        given(messageFrame.getContextVariable("HederaFunctionality")).willReturn(functionality);
        given(messageFrame.getMessageFrameStack()).willReturn(returningDeque);

        given(usagePricesProvider.defaultPricesGiven(functionality, timestamp)).willReturn(feeData);
        given(hbarCentExchange.rate(timestamp))
                .willReturn(ExchangeRate.newBuilder()
                        .setHbarEquiv(2000)
                        .setCentEquiv(200)
                        .build());

        assertEquals(1516L, subject.logOperationGasCost(messageFrame, 1L, 2L, 3));
        verify(messageFrame).getGasPrice();
        verify(messageFrame).getBlockValues();
        verify(messageFrame).getContextVariable("HederaFunctionality");
        verify(messageFrame).getMessageFrameStack();
        verify(usagePricesProvider).defaultPricesGiven(functionality, timestamp);
        verify(hbarCentExchange).rate(timestamp);
    }
}
