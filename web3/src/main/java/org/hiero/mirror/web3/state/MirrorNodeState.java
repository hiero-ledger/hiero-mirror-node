// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.fees.NoopFeeCharging.UNIVERSAL_NOOP_FEE_CHARGING;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.AppContext.Gossip;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.AppThrottleFactory;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.InstantSource;
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
import org.hiero.base.crypto.Hash;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.state.components.NoOpMetrics;
import org.hiero.mirror.web3.state.components.SchemaRegistryImpl;
import org.hiero.mirror.web3.state.components.entity.AppEntityIdFactory;
import org.hiero.mirror.web3.state.components.entity.EntityIdServiceImpl;
import org.hiero.mirror.web3.state.core.FunctionReadableSingletonState;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.hiero.mirror.web3.state.core.ListReadableQueueState;
import org.hiero.mirror.web3.state.core.ListWritableQueueState;
import org.hiero.mirror.web3.state.core.MapReadableStates;
import org.hiero.mirror.web3.state.core.MapWritableKVState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
@RequiredArgsConstructor
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<Integer, Object>> states = new ConcurrentHashMap<>();

    private final List<ReadableKVState> readableKVStates;
    private final ServicesRegistry servicesRegistry;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private static final CommonProperties commonProperties = CommonProperties.getInstance();
    private static final NodeInfoImpl DEFAULT_NODE_INFO = new NodeInfoImpl(
            0L,
            asAccount(commonProperties.getShard(), commonProperties.getRealm(), 3L),
            10L,
            List.of(),
            Bytes.EMPTY,
            List.of(),
            true,
            Bytes.EMPTY);
    private static final Metrics NO_OP_METRICS = new NoOpMetrics();

    @PostConstruct
    private void init() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            // If the flag is not enabled, we don't need to make any further initialization.
            return;
        }

        registerServices(servicesRegistry);

        servicesRegistry.registrations().forEach(registration -> {
            if (!(registration.registry() instanceof SchemaRegistryImpl schemaRegistry)) {
                throw new IllegalArgumentException("Can only be used with SchemaRegistryImpl instances");
            }

            schemaRegistry.migrate(
                    registration.serviceName(), this, mirrorNodeEvmProperties.getVersionedConfiguration());
        });
    }

    public MirrorNodeState addService(@NonNull final String serviceName, @NonNull final Map<Integer, ?> dataSources) {
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

    @NonNull
    @Override
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return readableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = this.states.get(s);
            if (serviceStates == null) {
                return new MapReadableStates(new HashMap<>());
            }
            final Map<Integer, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateId = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue queue) {
                    data.put(stateId, new ListReadableQueueState(serviceName, stateId, queue));
                } else if (state instanceof ReadableKVState<?, ?> kvState) {
                    final var readableKVState = readableKVStates.stream()
                            .filter(r -> r.getStateId() == stateId)
                            .findFirst();

                    if (readableKVState.isPresent()) {
                        data.put(stateId, readableKVState.get());
                    } else {
                        data.put(stateId, kvState);
                    }
                } else if (state instanceof SingletonState<?> singleton) {
                    data.put(stateId, new FunctionReadableSingletonState<>(serviceName, stateId, singleton));
                }
            }
            return new MapReadableStates(data);
        });
    }

    @NonNull
    @Override
    public WritableStates getWritableStates(@NonNull String serviceName) {
        return writableStates.computeIfAbsent(serviceName, s -> {
            final var serviceStates = states.get(s);
            if (serviceStates == null) {
                return new EmptyWritableStates();
            }
            final Map<Integer, Object> data = new ConcurrentHashMap<>();
            for (final var entry : serviceStates.entrySet()) {
                final var stateId = entry.getKey();
                final var state = entry.getValue();
                if (state instanceof Queue<?> queue) {
                    data.put(stateId, new ListWritableQueueState<>(serviceName, stateId, queue));
                } else if (state instanceof ReadableKVState<?, ?>) {
                    data.put(
                            stateId,
                            new MapWritableKVState<>(
                                    serviceName,
                                    stateId,
                                    getReadableStates(serviceName).get(stateId)));
                } else if (state instanceof SingletonState<?> ref) {
                    data.put(stateId, new FunctionWritableSingletonState<>(serviceName, stateId, ref));
                }
            }
            return new MapWritableStates(data, () -> readableStates.remove(serviceName));
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
                && Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(readableStates, writableStates, states);
    }

    @VisibleForTesting
    Map<String, Map<Integer, Object>> getStates() {
        return Collections.unmodifiableMap(states);
    }

    private void registerServices(final ServicesRegistry servicesRegistry) {
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
                () -> UNIVERSAL_NOOP_FEE_CHARGING,
                new AppEntityIdFactory(config));
        Set.of(
                        new EntityIdServiceImpl(),
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
                    @NonNull Key key,
                    @NonNull Bytes bytes,
                    SignatureVerifier.@NonNull MessageType messageType,
                    @NonNull SignatureMap signatureMap,
                    @Nullable Function<Key, SimpleKeyStatus> simpleKeyVerifier) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public KeyCounts countSimpleKeys(@NonNull Key key) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public void setHash(Hash hash) {
        // No-op
    }
}
