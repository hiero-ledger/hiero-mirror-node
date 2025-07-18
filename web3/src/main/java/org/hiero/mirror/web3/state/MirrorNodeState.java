// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.fees.NoopFeeCharging.NOOP_FEE_CHARGING;
import static com.swirlds.platform.state.service.PlatformStateFacade.DEFAULT_PLATFORM_STATE_FACADE;
import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.AppEntityIdFactory;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.AppContext.Gossip;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.AppThrottleFactory;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.handle.metric.UnavailableMetrics;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.time.Time;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleNodeState;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateMetadata;
import com.swirlds.state.lifecycle.info.NodeInfo;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.base.crypto.Hash;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.state.components.NoOpMetrics;
import org.hiero.mirror.web3.state.core.ListReadableQueueState;
import org.hiero.mirror.web3.state.core.ListWritableQueueState;
import org.hiero.mirror.web3.state.core.MapReadableStates;
import org.hiero.mirror.web3.state.core.MapWritableKVState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.singleton.SingletonState;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
@RequiredArgsConstructor
public class MirrorNodeState implements MerkleNodeState {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    private final List<ReadableKVState> readableKVStates;

    private final ServicesRegistry servicesRegistry;
    private final ServiceMigrator serviceMigrator;
    private final StartupNetworks startupNetworks;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final RecordFileRepository recordFileRepository;
    private final StoreMetricsServiceImpl storeMetricsService;
    private final ConfigProviderImpl configProvider;

    private static final CommonProperties commonProperties = CommonProperties.getInstance();
    private static final NodeInfo DEFAULT_NODE_INFO = new NodeInfoImpl(
            0L,
            asAccount(commonProperties.getShard(), commonProperties.getRealm(), 3L),
            10L,
            List.of(),
            Bytes.EMPTY,
            List.of(),
            true);
    private static final Metrics NO_OP_METRICS = new NoOpMetrics();

    @PostConstruct
    private void init() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            // If the flag is not enabled, we don't need to make any further initialization.
            return;
        }

        Optional<RecordFile> latest = recordFileRepository.findLatest();
        final var bootstrapConfig = mirrorNodeEvmProperties.getVersionedConfiguration();
        final var currentSemanticVersion =
                bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        final var currentVersion =
                bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        final var previousVersion = latest.isEmpty() ? null : currentVersion;
        ContractCallContext.run(ctx -> {
            latest.ifPresent(ctx::setRecordFile);
            registerServices(servicesRegistry);
            serviceMigrator.doMigrations(
                    this,
                    servicesRegistry,
                    previousVersion,
                    currentVersion,
                    mirrorNodeEvmProperties.getVersionedConfiguration(),
                    mirrorNodeEvmProperties.getVersionedConfiguration(),
                    UnavailableMetrics.UNAVAILABLE_METRICS,
                    startupNetworks,
                    storeMetricsService,
                    configProvider,
                    DEFAULT_PLATFORM_STATE_FACADE);
            return ctx;
        });
    }

    @Override
    public void init(Time time, Metrics metrics, MerkleCryptography merkleCryptography, LongSupplier roundSupplier) {
        // No-op
    }

    public MirrorNodeState addService(@Nonnull final String serviceName, @Nonnull final Map<String, ?> dataSources) {
        final var serviceStates = this.states.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
        dataSources.forEach((k, b) -> {
            if (!serviceStates.containsKey(k)) {
                serviceStates.put(k, b);
            }
        });

        // Purge any readable states whose state definitions are now stale,
        // since they don't include the new data sources we just added
        readableStates.remove(serviceName);
        writableStates.remove(serviceName);
        return this;
    }

    @Nonnull
    @Override
    public MerkleNodeState copy() {
        return this;
    }

    @Override
    public <T extends MerkleNode> void putServiceStateIfAbsent(
            @Nonnull StateMetadata<?, ?> md, @Nonnull Supplier<T> nodeSupplier, @Nonnull Consumer<T> nodeInitializer) {}

    @Override
    public void unregisterService(@Nonnull String serviceName) {}

    /**
     * Removes the state with the given key for the service with the given name.
     *
     * @param serviceName the name of the service
     * @param stateKey    the key of the state
     */
    public void removeServiceState(@Nonnull final String serviceName, @Nonnull final String stateKey) {
        requireNonNull(serviceName);
        requireNonNull(stateKey);
        this.states.computeIfPresent(serviceName, (k, v) -> {
            v.remove(stateKey);
            // Purge any readable states whose state definitions are now stale,
            // since they still include the data sources we just removed
            readableStates.remove(serviceName);
            writableStates.remove(serviceName);
            return v;
        });
    }

    @Nonnull
    @Override
    public ReadableStates getReadableStates(@Nonnull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = this.states.get(s);
            if (serviceStates == null) {
                return new MapReadableStates(new HashMap<>());
            }
            final Map<String, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue queue) {
                    data.put(stateName, new ListReadableQueueState(stateName, queue));
                } else if (state instanceof ReadableKVState<?, ?> kvState) {
                    final var readableKVState = readableKVStates.stream()
                            .filter(r -> r.getStateKey().equals(stateName))
                            .findFirst();

                    if (readableKVState.isPresent()) {
                        data.put(stateName, readableKVState.get());
                    } else {
                        data.put(stateName, kvState);
                    }
                } else if (state instanceof SingletonState<?> singleton) {
                    data.put(stateName, new ReadableSingletonStateBase<>(stateName, singleton));
                }
            }
            return new MapReadableStates(data);
        });
    }

    @Nonnull
    @Override
    public WritableStates getWritableStates(@Nonnull String serviceName) {
        return writableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = states.get(s);
            if (serviceStates == null) {
                return new EmptyWritableStates();
            }
            final Map<String, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateName = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue<?> queue) {
                    data.put(
                            stateName,
                            withAnyRegisteredListeners(serviceName, new ListWritableQueueState<>(stateName, queue)));
                } else if (state instanceof ReadableKVState<?, ?>) {
                    data.put(
                            stateName,
                            withAnyRegisteredListeners(
                                    serviceName,
                                    new MapWritableKVState<>(
                                            stateName,
                                            getReadableStates(serviceName).get(stateName))));
                } else if (state instanceof SingletonState<?> ref) {
                    data.put(stateName, withAnyRegisteredListeners(serviceName, stateName, ref));
                }
            }
            return new MapWritableStates(data, () -> readableStates.remove(serviceName));
        });
    }

    @Override
    public void registerCommitListener(@Nonnull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void unregisterCommitListener(@Nonnull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.remove(listener);
    }

    public void commit() {
        writableStates.values().forEach(writableStatesValue -> {
            if (writableStatesValue instanceof MapWritableStates mapWritableStates) {
                mapWritableStates.commit();
            }
        });
    }

    private <V> WritableSingletonStateBase<V> withAnyRegisteredListeners(
            @Nonnull final String serviceName,
            @Nonnull final String stateKey,
            @Nonnull final SingletonState<V> singleton) {
        final var state = new WritableSingletonStateBase<>(stateKey, singleton, singleton::set);
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(SINGLETON)) {
                registerSingletonListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <K, V> MapWritableKVState<K, V> withAnyRegisteredListeners(
            @Nonnull final String serviceName, @Nonnull final MapWritableKVState<K, V> state) {
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(MAP)) {
                registerKVListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <T> ListWritableQueueState<T> withAnyRegisteredListeners(
            @Nonnull final String serviceName, @Nonnull final ListWritableQueueState<T> state) {
        listeners.forEach(listener -> {
            if (listener.stateTypes().contains(QUEUE)) {
                registerQueueListener(serviceName, state, listener);
            }
        });
        return state;
    }

    private <V> void registerSingletonListener(
            @Nonnull final String serviceName,
            @Nonnull final WritableSingletonStateBase<V> singletonState,
            @Nonnull final StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, singletonState.getStateKey());
        singletonState.registerListener(value -> listener.singletonUpdateChange(stateId, value));
    }

    private <V> void registerQueueListener(
            @Nonnull final String serviceName,
            @Nonnull final WritableQueueStateBase<V> queueState,
            @Nonnull final StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, queueState.getStateKey());
        queueState.registerListener(new QueueChangeListener<>() {
            @Override
            public void queuePushChange(@Nonnull final V value) {
                listener.queuePushChange(stateId, value);
            }

            @Override
            public void queuePopChange() {
                listener.queuePopChange(stateId);
            }
        });
    }

    private <K, V> void registerKVListener(
            @Nonnull final String serviceName, WritableKVStateBase<K, V> state, StateChangeListener listener) {
        final var stateId = listener.stateIdFor(serviceName, state.getStateKey());
        state.registerListener(new KVChangeListener<>() {
            @Override
            public void mapUpdateChange(@Nonnull final K key, @Nonnull final V value) {
                listener.mapUpdateChange(stateId, key, value);
            }

            @Override
            public void mapDeleteChange(@Nonnull final K key) {
                listener.mapDeleteChange(stateId, key);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MirrorNodeState that = (MirrorNodeState) o;
        return Objects.equals(readableStates, that.readableStates)
                && Objects.equals(writableStates, that.writableStates)
                && Objects.equals(states, that.states)
                && Objects.equals(listeners, that.listeners);
    }

    @Override
    public int hashCode() {
        return Objects.hash(readableStates, writableStates, states, listeners);
    }

    @VisibleForTesting
    void setWritableStates(final Map<String, WritableStates> writableStates) {
        this.writableStates.putAll(writableStates);
    }

    @VisibleForTesting
    Map<String, Map<String, Object>> getStates() {
        return Collections.unmodifiableMap(states);
    }

    private void registerServices(ServicesRegistry servicesRegistry) {
        // Register all service schema RuntimeConstructable factories before platform init
        final var config = mirrorNodeEvmProperties.getVersionedConfiguration();
        final var appContext = new AppContextImpl(
                InstantSource.system(),
                signatureVerifier(),
                Gossip.UNAVAILABLE_GOSSIP,
                () -> config,
                () -> DEFAULT_NODE_INFO,
                () -> NO_OP_METRICS,
                new AppThrottleFactory(
                        () -> config, () -> this, () -> ThrottleDefinitions.DEFAULT, ThrottleAccumulator::new),
                () -> NOOP_FEE_CHARGING,
                new AppEntityIdFactory(config));
        Set.of(
                        new EntityIdService(),
                        new TokenServiceImpl(appContext),
                        new FileServiceImpl(),
                        new ContractServiceImpl(appContext, NO_OP_METRICS),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new RecordCacheService(),
                        new ScheduleServiceImpl(appContext),
                        new BlockStreamService())
                .forEach(servicesRegistry::register);
    }

    private SignatureVerifier signatureVerifier() {
        return new SignatureVerifier() {
            @Override
            public boolean verifySignature(
                    @Nonnull Key key,
                    @Nonnull com.hedera.pbj.runtime.io.buffer.Bytes bytes,
                    @Nonnull com.hedera.node.app.spi.signatures.SignatureVerifier.MessageType messageType,
                    @Nonnull SignatureMap signatureMap,
                    @Nullable Function<Key, SimpleKeyStatus> simpleKeyVerifier) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public KeyCounts countSimpleKeys(@Nonnull Key key) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public void setHash(Hash hash) {
        // No-op
    }
}
