// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.contract.impl.schemas.V065ContractSchema.EVM_HOOK_STATES_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V065ContractSchema.LAMBDA_STORAGE_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_COUNTS_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_USAGES_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema.NODE_REWARDS_STATE_ID;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TRANSACTION_RECEIPTS_STATE_ID;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import org.hiero.base.crypto.Hash;
import org.hiero.mirror.web3.state.core.FunctionReadableSingletonState;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.hiero.mirror.web3.state.core.ListReadableQueueState;
import org.hiero.mirror.web3.state.core.ListWritableQueueState;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.core.MapReadableStates;
import org.hiero.mirror.web3.state.core.MapWritableKVState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.keyvalue.AbstractReadableKVState;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.jspecify.annotations.NonNull;

@Named
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<Integer, Object>> states = new HashMap<>();

    private final Collection<AbstractReadableKVState<?, ?>> readableKVStates;

    public MirrorNodeState(
            final Collection<SingletonState<?>> singletonStates,
            final Collection<AbstractReadableKVState<?, ?>> readableKVStates) {
        this.readableKVStates = readableKVStates;
        initSingletonStates(singletonStates);
        initKVStates(readableKVStates);
        initQueueStates();
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
                if (state instanceof Queue<?> queue) {
                    data.put(stateId, new ListReadableQueueState<>(serviceName, stateId, queue));
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
    public void setHash(Hash hash) {
        // No-op
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

    private void initSingletonStates(final Collection<SingletonState<?>> singletonStates) {
        singletonStates.forEach(
                singletonState -> states.computeIfAbsent(singletonState.getServiceName(), k -> new HashMap<>())
                        .put(singletonState.getId(), singletonState));
        Map<Integer, String> defaultSingletonImplementations = new HashMap<>(Map.of(
                STAKING_NETWORK_REWARDS_STATE_ID, TokenService.NAME,
                NODE_REWARDS_STATE_ID, TokenService.NAME));
        defaultSingletonImplementations.forEach((stateId, serviceName) ->
                states.computeIfAbsent(serviceName, k -> new HashMap<>()).put(stateId, new DefaultSingleton(stateId)));
    }

    private void initKVStates(final Collection<AbstractReadableKVState<?, ?>> readableKVStates) {
        readableKVStates.forEach(kvState -> states.computeIfAbsent(kvState.getServiceName(), k -> new HashMap<>())
                .put(kvState.getStateId(), kvState));
        Map<Integer, String> defaultKvImplementations = new HashMap<>(Map.of(
                LAMBDA_STORAGE_STATE_ID, ContractService.NAME,
                EVM_HOOK_STATES_STATE_ID, ContractService.NAME,
                SCHEDULE_ID_BY_EQUALITY_STATE_ID, ScheduleService.NAME,
                SCHEDULED_COUNTS_STATE_ID, ScheduleService.NAME,
                SCHEDULED_USAGES_STATE_ID, ScheduleService.NAME));
        defaultKvImplementations.forEach(
                (stateId, serviceName) -> states.computeIfAbsent(serviceName, k -> new HashMap<>())
                        .put(stateId, createMapReadableStateForId(serviceName, stateId)));
    }

    private void initQueueStates() {
        states.put(RecordCacheService.NAME, new HashMap<>(Map.of(TRANSACTION_RECEIPTS_STATE_ID, new LinkedList<>())));
    }

    private MapReadableKVState<?, ?> createMapReadableStateForId(final String serviceName, int id) {
        return new MapReadableKVState<>(serviceName, id, new HashMap<>());
    }
}
