/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state;

import static com.hedera.node.app.workflows.handle.metric.UnavailableMetrics.UNAVAILABLE_METRICS;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.DEFAULT_NODE_INFO;
import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.core.ListReadableQueueState;
import com.hedera.mirror.web3.state.core.ListWritableQueueState;
import com.hedera.mirror.web3.state.core.MapReadableKVState;
import com.hedera.mirror.web3.state.core.MapReadableStates;
import com.hedera.mirror.web3.state.core.MapWritableKVState;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.mirror.web3.state.singleton.SingletonState;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
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
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.app.workflows.handle.metric.UnavailableMetrics;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
@RequiredArgsConstructor
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();
    private final List<StateChangeListener> listeners = new ArrayList<>();

    private final List<ReadableKVState> readableKVStates;

    private final ServicesRegistry servicesRegistry;
    private final ServiceMigrator serviceMigrator;
    private final NetworkInfo networkInfo;
    private final StartupNetworks startupNetworks;

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @PostConstruct
    private void init() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            // If the flag is not enabled, we don't need to make any further initialization.
            return;
        }

        ContractCallContext.run(ctx -> {
            registerServices(servicesRegistry);
            final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
            serviceMigrator.doMigrations(
                    this,
                    servicesRegistry,
                    null,
                    new ServicesSoftwareVersion(
                            bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                    mirrorNodeEvmProperties.getVersionedConfiguration(),
                    mirrorNodeEvmProperties.getVersionedConfiguration(),
                    networkInfo,
                    UnavailableMetrics.UNAVAILABLE_METRICS,
                    startupNetworks);
            return ctx;
        });
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
                } else if (state instanceof Map map) {
                    final var readableKVState = readableKVStates.stream()
                            .filter(r -> r.getStateKey().equals(stateName))
                            .findFirst();

                    if (readableKVState.isPresent()) {
                        data.put(stateName, readableKVState.get());
                    } else {
                        data.put(stateName, new MapReadableKVState(stateName, map));
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
                } else if (state instanceof Map<?, ?>) {
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

        final var appContext = new AppContextImpl(
                InstantSource.system(),
                signatureVerifier(),
                Gossip.UNAVAILABLE_GOSSIP,
                () -> mirrorNodeEvmProperties.getVersionedConfiguration(),
                () -> DEFAULT_NODE_INFO,
                () -> UNAVAILABLE_METRICS,
                new AppThrottleFactory(
                        () -> mirrorNodeEvmProperties.getVersionedConfiguration(),
                        () -> this,
                        () -> ThrottleDefinitions.DEFAULT,
                        ThrottleAccumulator::new));
        Set.of(
                        new EntityIdService(),
                        new TokenServiceImpl(),
                        new FileServiceImpl(),
                        new ContractServiceImpl(appContext, UNAVAILABLE_METRICS),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new RecordCacheService(),
                        new ScheduleServiceImpl())
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
}
