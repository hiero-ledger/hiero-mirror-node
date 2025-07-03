// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.services.txns.util.PrngLogic;
import java.time.Instant;
import java.util.Map;
import org.hiero.base.utility.CommonUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.pricing.RatesAndFeesLoader;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class MirrorEvmMessageCallProcessorBaseTest {

    private static final byte[] WELL_KNOWN_HASH_BYTE_ARRAY = CommonUtils.unhex(
            "65386630386164632d356537632d343964342d623437372d62636134346538386338373133633038316162372d6163");

    private static MockedStatic<ContractCallContext> contextMockedStatic;
    private final CommonProperties commonProperties = new CommonProperties();

    @Mock
    AbstractAutoCreationLogic autoCreationLogic;

    @Mock
    EntityAddressSequencer entityAddressSequencer;

    @Mock
    MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    EVM evm;

    @Mock
    PrecompileContractRegistry precompiles;

    @Mock
    MessageFrame messageFrame;

    @Mock
    TraceProperties traceProperties;

    @Mock
    HederaEvmStackedWorldStateUpdater updater;

    @Mock
    Store store;

    @Mock
    OperationTracer operationTracer;

    @Mock
    GasCalculatorHederaV22 gasCalculatorHederaV22;

    @Mock
    EvmInfrastructureFactory evmInfrastructureFactory;

    final EvmHTSPrecompiledContract htsPrecompiledContract = new EvmHTSPrecompiledContract(evmInfrastructureFactory);

    @Mock
    PrecompilePricingUtils precompilePricingUtils;

    @Mock
    RatesAndFeesLoader ratesAndFeesLoader;

    final ExchangeRatePrecompiledContract exchangeRatePrecompiledContract = new ExchangeRatePrecompiledContract(
            gasCalculatorHederaV22,
            new BasicHbarCentExchange(ratesAndFeesLoader),
            new MirrorNodeEvmProperties(commonProperties, new SystemEntity(commonProperties)),
            Instant.now());
    final PrngSystemPrecompiledContract prngSystemPrecompiledContract = new PrngSystemPrecompiledContract(
            gasCalculatorHederaV22,
            new PrngLogic(() -> WELL_KNOWN_HASH_BYTE_ARRAY),
            new LivePricesSource(
                    new BasicHbarCentExchange(ratesAndFeesLoader), new BasicFcfsUsagePrices(ratesAndFeesLoader)),
            precompilePricingUtils);
    final Map<String, PrecompiledContract> hederaPrecompileList = Map.of(
            EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS, htsPrecompiledContract,
            EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS, exchangeRatePrecompiledContract,
            PRNG_PRECOMPILE_ADDRESS, prngSystemPrecompiledContract);

    @Spy
    protected ContractCallContext contractCallContext;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setUpContext() {
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }
}
