// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType.NOOP_THROTTLE;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.DISABLE_THROTTLES_PROPERTY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.hints.impl.HintsLibraryImpl;
import com.hedera.node.app.hints.impl.HintsServiceImpl;
import com.hedera.node.app.hints.impl.RsaContext;
import com.hedera.node.app.history.impl.HistoryLibraryImpl;
import com.hedera.node.app.history.impl.HistoryServiceImpl;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.records.impl.producers.formats.SelfNodeAccountIdManagerImpl;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.clpr.impl.ClprServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.exec.scope.DefaultVerificationStrategies;
import com.hedera.node.app.service.entityid.EntityIdFactory;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.throttle.AppScheduleThrottleFactory;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.standalone.DaggerExecutorComponent;
import com.hedera.node.app.workflows.standalone.ExecutorComponent;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.TracerBinding;
import com.hedera.node.app.workflows.standalone.impl.StandaloneNetworkInfo;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.consensus.metrics.noop.NoOpMetrics;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * Creates and reuses Mirror Node standalone transaction executors using custom contract-key verification.
 *
 * <p>Upstream {@link com.hedera.node.app.workflows.standalone.TransactionExecutors} hard-codes
 * {@code NOOP_VERIFICATION_STRATEGIES} when constructing {@link ContractServiceImpl} and exposes no option to replace
 * it. This factory copies upstream's {@code newExecutorComponent()} configuration providers, application context,
 * service construction, and Dagger wiring so it can inject {@link SimulationVerificationStrategies} into the contract
 * service. The copied setup and executor initialization must stay aligned with the pinned consensus-node dependency.
 *
 * <p>The custom strategy delegates {@code CONTRACT_ID} and {@code DELEGATABLE_CONTRACT_ID} checks to
 * {@link DefaultVerificationStrategies}, including keys nested in key lists and thresholds, while accepting
 * cryptographic keys for simulation. This allows HTS transfers to select an allowance spend when the calling contract
 * cannot authorize the debited account through its contract key. The standalone dispatch's upstream no-op key verifier
 * is unchanged; these executors remain intended for simulation.
 *
 * <p>Additional mirror customizations are executor reuse by EVM version with asynchronous startup initialization,
 * scoped tracer bindings, and the mirror block-hash operation. Each executor's {@link ExchangeRateManager} is retained
 * for latest-rate refresh by {@link ExchangeRateUpdater}; historical rate selection is handled by the manager override.
 *
 * @see com.hedera.node.app.workflows.standalone.TransactionExecutors
 */
@Named
@RequiredArgsConstructor
public class TransactionExecutorFactory {

    private static final SimulationVerificationStrategies simulationVerificationStrategies =
            new SimulationVerificationStrategies();
    private static final ScopedTracerBinding scopedTracerBinding = new ScopedTracerBinding();
    private final EntityIdFactory entityIdFactory;
    private final EvmProperties evmProperties;
    private final BlockHashOperation mirrorBlockHashOperation;
    private final MirrorNodeState mirrorNodeState;
    private final Map<SemanticVersion, Executor> transactionExecutors = new ConcurrentHashMap<>();

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // Create transaction executor for each EVM version, on startup, before the k8s
        // readiness probe elapses, so we avoid slowing down the initial contract calls.
        ContractCallContext.run(ctx -> {
            evmProperties.getEvmVersions().values().forEach(this::create);
            return ctx;
        });
    }

    // Reuse TransactionExecutor across requests for the same EVM version
    public TransactionExecutor get() {
        var version = evmProperties.getSemanticEvmVersion();
        return transactionExecutors.computeIfAbsent(version, this::create).transactionExecutor();
    }

    /**
     * Returns the exchange rate managers of the transaction executors created so far. Each executor keeps its own
     * in-memory copy of the rates, and {@link ExchangeRateUpdater} reloads them after the current rate expires.
     */
    List<ExchangeRateManager> getExchangeRateManagers() {
        return transactionExecutors.values().stream()
                .map(Executor::exchangeRateManager)
                .toList();
    }

    // Mirrors TransactionExecutors.newExecutor() but keeps a reference to the component's ExchangeRateManager
    private synchronized Executor create(SemanticVersion evmVersion) {
        var appProperties = new HashMap<>(evmProperties.getTransactionProperties());
        appProperties.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());

        final var component = newExecutorComponent(appProperties);
        component.stateNetworkInfo().initFrom(mirrorNodeState);
        final var streamMode = component
                .configProvider()
                .getConfiguration()
                .getConfigData(BlockStreamConfig.class)
                .streamMode();
        component.initializer().initialize(mirrorNodeState, streamMode);

        final var exchangeRateManager = component.exchangeRateManager();
        final TransactionExecutor transactionExecutor = (transactionBody, consensusNow, tracers) -> {
            final var dispatch =
                    component.standaloneDispatchFactory().newDispatch(mirrorNodeState, transactionBody, consensusNow);
            scopedTracerBinding.runWhere(
                    List.of(tracers), () -> component.dispatchProcessor().processDispatch(dispatch));
            return dispatch.stack()
                    .buildHandleOutput(consensusNow, exchangeRateManager.exchangeRates())
                    .singleTransactionRecords();
        };

        return new Executor(transactionExecutor, exchangeRateManager);
    }

    @SuppressWarnings("removal")
    private ExecutorComponent newExecutorComponent(final Map<String, String> properties) {
        final var bootstrapConfigProvider = new BootstrapConfigProviderImpl(properties);
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        final var configProvider = new ConfigProviderImpl(false, null, properties);
        final var componentRef = new AtomicReference<ExecutorComponent>();
        final var defaultNodeInfo = new NodeInfoImpl(
                0, entityIdFactory.newAccountId(3L), 10, List.of(), Bytes.EMPTY, List.of(), false, null);
        final var disableThrottles = Boolean.parseBoolean(properties.get(DISABLE_THROTTLES_PROPERTY));
        final var metrics = new NoOpMetrics();

        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl()),
                UNAVAILABLE_GOSSIP,
                bootstrapConfigProvider::getConfiguration,
                () -> defaultNodeInfo,
                () -> metrics,
                new AppScheduleThrottleFactory(
                        configProvider::getConfiguration,
                        () -> mirrorNodeState,
                        () -> componentRef.get().throttleServiceManager().activeThrottleDefinitionsOrThrow(),
                        (configSupplier, capacitySplitSource, throttleType) -> disableThrottles
                                ? new ThrottleAccumulator(configSupplier, capacitySplitSource, NOOP_THROTTLE)
                                : new ThrottleAccumulator(configSupplier, capacitySplitSource, throttleType)),
                () -> componentRef.get().appFeeCharging(),
                entityIdFactory);
        final var contractService = new ContractServiceImpl(
                appContext,
                metrics,
                simulationVerificationStrategies,
                scopedTracerBinding,
                Set.of(mirrorBlockHashOperation));
        final var utilService = new UtilServiceImpl(
                appContext,
                (signedTxn, config) -> componentRef
                        .get()
                        .transactionChecker()
                        .parseSignedAndCheck(
                                signedTxn,
                                config.getConfigData(HederaConfig.class).nodeTransactionMaxBytes())
                        .txBody());
        final var fileService = new FileServiceImpl();
        final var scheduleService = new ScheduleServiceImpl(appContext);
        final var hintsService = new HintsServiceImpl(
                metrics,
                ForkJoinPool.commonPool(),
                appContext,
                new HintsLibraryImpl(),
                bootstrapConfig.getConfigData(BlockStreamConfig.class).blockPeriod(),
                new RsaContext(appContext.configSupplier()),
                new ConcurrentHashMap<>());
        final var historyService =
                new HistoryServiceImpl(metrics, ForkJoinPool.commonPool(), appContext, new HistoryLibraryImpl());
        final var component = DaggerExecutorComponent.builder()
                .appContext(appContext)
                .configProviderImpl(configProvider)
                .disableThrottles(disableThrottles)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .fileServiceImpl(fileService)
                .tokenServiceImpl(new TokenServiceImpl(appContext))
                .clprServiceImpl(new ClprServiceImpl())
                .consensusServiceImpl(new ConsensusServiceImpl())
                .networkServiceImpl(new NetworkServiceImpl())
                .contractServiceImpl(contractService)
                .utilServiceImpl(utilService)
                .scheduleServiceImpl(scheduleService)
                .hintsService(hintsService)
                .historyService(historyService)
                .addressBookService(new AddressBookServiceImpl())
                .metrics(metrics)
                .throttleFactory(appContext.throttleFactory())
                .selfNodeAccountIdManager(new SelfNodeAccountIdManagerImpl(
                        configProvider, new StandaloneNetworkInfo(configProvider), mirrorNodeState))
                .build();
        componentRef.set(component);
        return component;
    }

    private record Executor(TransactionExecutor transactionExecutor, ExchangeRateManager exchangeRateManager) {}

    private static final class ScopedTracerBinding implements TracerBinding {
        private static final ScopedValue<List<ActionSidecarContentTracer>> TRACERS = ScopedValue.newInstance();

        @Override
        public void runWhere(final List<ActionSidecarContentTracer> tracers, final Runnable runnable) {
            ScopedValue.where(TRACERS, tracers).run(runnable);
        }

        @Override
        public List<ActionSidecarContentTracer> get() {
            return TRACERS.orElse(List.of());
        }
    }
}
