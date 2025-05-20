// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.hiero.mirror.web3.evm.store.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecompilePricingUtilsTest {

    private static final long COST = 36;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long GAS_PRICE = 77;
    private static final long MINIMUM_GAS_COST = 100;
    public static final BigDecimal USD_TO_TINYCENTS = BigDecimal.valueOf(100 * 100_000_000L);

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private BasicHbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private BasicFcfsUsagePrices resourceCosts;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Store store;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

    @Mock
    private Precompile precompile;

    @Mock
    private TransactionBody.Builder transactionBody;

    @Test
    void failsToLoadCanonicalPrices() throws IOException {
        given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
        assertThrows(
                PrecompilePricingUtils.CanonicalOperationsUnloadableException.class,
                () -> new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory));
    }

    @Test
    void calculatesMinimumPrice() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(exchange.rate(timestamp)).willReturn(exchangeRate);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        final long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE, timestamp);

        assertEquals(
                USD_TO_TINYCENTS
                        .multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE))
                        .longValue(),
                price);
    }

    @Test
    void computeViewFunctionGasMinimumTest() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(1, 2, 3);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculator.estimatePayment(any(), any(), any(), any(ResponseType.class)))
                .willReturn(feeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(GAS_PRICE);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        // minimum gas cost should apply
        final long price = subject.computeViewFunctionGas(timestamp, MINIMUM_GAS_COST);

        assertEquals(MINIMUM_GAS_COST, price);
    }

    @Test
    void computeViewFunctionGasTest() throws IOException {
        final long nodeFee = 10000;
        final long networkFee = 20000;
        final long serviceFee = 30000;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        FeeObject feeObject = new FeeObject(nodeFee, networkFee, serviceFee);
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculator.estimatePayment(any(), any(), any(), any(ResponseType.class)))
                .willReturn(feeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(GAS_PRICE);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        final long price = subject.computeViewFunctionGas(timestamp, MINIMUM_GAS_COST);
        final long expectedPrice = (nodeFee + networkFee + serviceFee + GAS_PRICE - 1L) / GAS_PRICE;

        // The minimum gas cost does not apply.  The cost is the expected price plus 20%.
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }

    @Test
    void computeGasRequirementMinimumTest() throws IOException {
        final long gasInTinybars = 10000;
        final long minimumFeeInTinybars = 20000;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(GAS_PRICE);
        given(precompile.getMinimumFeeInTinybars(any(), any(), any())).willReturn(minimumFeeInTinybars);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
        final var subjectSpy = spy(subject);

        doReturn(gasInTinybars).when(subjectSpy).gasFeeInTinybars(any(), any());

        final long price =
                subjectSpy.computeGasRequirement(timestamp.getSeconds(), precompile, transactionBody, sender);
        final long expectedPrice = (minimumFeeInTinybars + GAS_PRICE - 1L) / GAS_PRICE;

        // The minimum gas should apply
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }

    @Test
    void computeGasRequirementTest() throws IOException {
        final long gasInTinybars = 10000;
        final long minimumFeeInTinybars = 500;
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(GAS_PRICE);
        given(precompile.getMinimumFeeInTinybars(any(), any(), any())).willReturn(minimumFeeInTinybars);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
        final var subjectSpy = spy(subject);

        doReturn(gasInTinybars).when(subjectSpy).gasFeeInTinybars(any(), any());

        final long price =
                subjectSpy.computeGasRequirement(timestamp.getSeconds(), precompile, transactionBody, sender);
        final long expectedPrice = (gasInTinybars + GAS_PRICE - 1L) / GAS_PRICE;

        // The minimum gas cost does not apply.  The cost is the expected price plus 20%.
        assertEquals(expectedPrice + expectedPrice / 5, price);
    }
}
