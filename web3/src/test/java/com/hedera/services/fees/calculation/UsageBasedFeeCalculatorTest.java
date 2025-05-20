// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getFeeObject;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.token.txns.TokenDissociateResourceUsage;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageBasedFeeCalculatorTest {
    private final FeeComponents mockFees = FeeComponents.newBuilder()
            .setMax(1_234_567L)
            .setGas(5_000_000L)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private final FeeData mockFeeData = FeeData.newBuilder()
            .setNetworkdata(mockFees)
            .setNodedata(mockFees)
            .setServicedata(mockFees)
            .setSubType(SubType.DEFAULT)
            .build();
    private final Map<SubType, FeeData> currentPrices = Map.of(
            SubType.DEFAULT, mockFeeData,
            SubType.TOKEN_FUNGIBLE_COMMON, mockFeeData,
            SubType.TOKEN_NON_FUNGIBLE_UNIQUE, mockFeeData);
    private final FeeData defaultCurrentPrices = mockFeeData;
    private final FeeData resourceUsage = mockFeeData;
    private final ExchangeRate currentRate =
            ExchangeRate.newBuilder().setCentEquiv(22).setHbarEquiv(1).build();
    private Query query;
    private final Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private HbarCentExchange exchange;
    private UsagePricesProvider usagePrices;
    private TxnResourceUsageEstimator correctOpEstimator;
    private QueryResourceUsageEstimator correctQueryEstimator;
    private QueryResourceUsageEstimator incorrectQueryEstimator;
    private Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;

    /* Has nine simple keys. */
    private JKey payerKey;
    private Transaction signedTxn;
    private SignedTxnAccessor accessor;
    private PricedUsageCalculator pricedUsageCalculator;

    private final AtomicLong suggestedMultiplier = new AtomicLong(1L);

    @Mock
    private Store store;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

    private UsageBasedFeeCalculator subject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Throwable {
        query = mock(Query.class);
        // payerKey = complexKey.asJKey();
        exchange = mock(HbarCentExchange.class);
        signedTxn = signableTxn(0).build();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray(), signedTxn);
        usagePrices = mock(UsagePricesProvider.class);
        correctOpEstimator = mock(TxnResourceUsageEstimator.class);
        correctQueryEstimator = mock(QueryResourceUsageEstimator.class);
        incorrectQueryEstimator = mock(QueryResourceUsageEstimator.class);
        pricedUsageCalculator = mock(PricedUsageCalculator.class);

        txnUsageEstimators = (Map<HederaFunctionality, List<TxnResourceUsageEstimator>>) mock(Map.class);

        subject = new UsageBasedFeeCalculator(
                exchange,
                usagePrices,
                pricedUsageCalculator,
                Set.of(incorrectQueryEstimator, correctQueryEstimator),
                txnUsageEstimators);
    }

    @Test
    void estimatesFutureGasPriceInTinybars() {
        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.defaultPricesGiven(CryptoCreate, at)).willReturn(defaultCurrentPrices);
        // and:
        final long expected = getTinybarsFromTinyCents(currentRate, mockFees.getGas() / FEE_DIVISOR_FACTOR);

        // when:
        final long actual = subject.estimatedGasPriceInTinybars(CryptoCreate, at);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void failsWithNpeGivenApplicableButUnusableCalculator() {
        // setup:
        givenApplicableButUnusableCalculator();

        // when:
        assertThrows(NullPointerException.class, () -> subject.computeFee(accessor, payerKey, at));
    }

    @Test
    void invokesQueryDelegateByTypeAsExpected() {
        // setup:
        final FeeObject expectedFees = getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(correctQueryEstimator.applicableTo(query)).willReturn(true);
        given(correctQueryEstimator.usageGivenType(query)).willReturn(resourceUsage);
        given(exchange.rate(at)).willReturn(currentRate);

        // when:
        final FeeObject fees = subject.estimatePayment(query, currentPrices.get(SubType.DEFAULT), at, ANSWER_ONLY);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void usesMultiplierAsExpected() {
        // setup:
        final long multiplier = 1L;
        final FeeObject expectedFees =
                getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate, multiplier);
        suggestedMultiplier.set(multiplier);

        givenApplicableButUnusableCalculator();
        given(correctOpEstimator.usageGiven(any(), any())).willReturn(resourceUsage);
        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.activePrices(any())).willReturn(currentPrices);

        // when:
        final FeeObject fees = subject.computeFee(accessor, payerKey, at);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void defaultFeeIfAccountMissing() {
        // setup:
        correctOpEstimator = new TokenDissociateResourceUsage(mock(EstimatorFactory.class), mock(StoreImpl.class));
        final FeeData expectedFeeData = FeeData.getDefaultInstance();

        // when:
        final var feeData = correctOpEstimator.usageGiven(accessor.getTxn(), subject.getSigUsage(accessor, payerKey));

        // then:
        assertEquals(expectedFeeData, feeData);
    }

    @Test
    void failsWithNseeGetTxnUsageEstimator() {
        final Exception exception =
                assertThrows(NoSuchElementException.class, () -> subject.computeFee(accessor, payerKey, at));
        assertEquals("No estimator exists for the given transaction", exception.getMessage());
    }

    @Test
    void failsWithNseeGetQueryUsageEstimator() {
        FeeData usgPrices = currentPrices.get(DEFAULT);
        final Exception exception = assertThrows(
                NoSuchElementException.class, () -> subject.estimatePayment(query, usgPrices, at, ANSWER_ONLY));
        assertEquals("No estimator exists for the given query", exception.getMessage());
    }

    @SuppressWarnings("deprecation")
    private Transaction.Builder signableTxn(final long fee) {
        final TransactionBody.Builder txn = baseTxn();
        txn.setTransactionFee(fee);
        return Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(txn.build().toByteArray()));
    }

    private TransactionBody.Builder baseTxn() {
        return TransactionBody.newBuilder()
                .setNodeAccountID(asAccount("0.0.3"))
                .setTransactionValidDuration(
                        Duration.newBuilder().setSeconds(68).build())
                .setMemo("memo");
    }

    private void givenApplicableButUnusableCalculator() {
        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(txnUsageEstimators.get(any())).willReturn(List.of(correctOpEstimator));
    }
}
