// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.entityid.EntityIdFactory;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.TracerBinding;
import com.hedera.node.config.data.BlockStreamConfig;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@Named
@RequiredArgsConstructor
public class TransactionExecutorFactory {

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

        final var component = TransactionExecutors.TRANSACTION_EXECUTORS.newExecutorComponent(
                mirrorNodeState,
                appProperties,
                ScopedTracerBinding.INSTANCE,
                Set.of(mirrorBlockHashOperation),
                entityIdFactory);
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
            ScopedTracerBinding.INSTANCE.runWhere(
                    List.of(tracers), () -> component.dispatchProcessor().processDispatch(dispatch));
            return dispatch.stack()
                    .buildHandleOutput(consensusNow, exchangeRateManager.exchangeRates())
                    .singleTransactionRecords();
        };

        return new Executor(transactionExecutor, exchangeRateManager);
    }

    private record Executor(TransactionExecutor transactionExecutor, ExchangeRateManager exchangeRateManager) {}

    private enum ScopedTracerBinding implements TracerBinding {
        INSTANCE;

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
