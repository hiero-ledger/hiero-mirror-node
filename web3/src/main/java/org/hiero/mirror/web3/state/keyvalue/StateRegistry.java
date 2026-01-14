// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.google.common.collect.ImmutableMap;
import com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema;
import com.hedera.node.app.service.contract.impl.schemas.V065ContractSchema;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema;
import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableKVState;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.jspecify.annotations.NonNull;

@Named
public final class StateRegistry {
    private static final Set<String> DEFAULT_IMPLEMENTATIONS = Stream.of(
                    V0490TokenSchema.STAKING_INFOS_KEY,
                    V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY,
                    V0490FileSchema.FILES_KEY,
                    V0490RecordCacheSchema.TRANSACTION_RECEIPTS_KEY,
                    V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY,
                    V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY,
                    V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY,
                    V0570ScheduleSchema.SCHEDULED_COUNTS_KEY,
                    V0570ScheduleSchema.SCHEDULED_ORDERS_KEY,
                    V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY,
                    V0570ScheduleSchema.SCHEDULED_USAGES_KEY,
                    V0610TokenSchema.NODE_REWARDS_KEY,
                    V065ContractSchema.EVM_HOOK_STATES_KEY,
                    V065ContractSchema.LAMBDA_STORAGE_KEY)
            .collect(Collectors.toSet());

    private final ImmutableMap<Integer, Object> states;

    public StateRegistry(
            @NonNull final Collection<ReadableKVState<?, ?>> keyValues,
            @NonNull final Collection<SingletonState<?>> singletons) {

        this.states = ImmutableMap.<Integer, Object>builder()
                .putAll(keyValues.stream().collect(Collectors.toMap(ReadableKVState::getStateId, kv -> kv)))
                .putAll(singletons.stream().collect(Collectors.toMap(SingletonState::getId, s -> s)))
                .build();
    }

    /**
     * Looks up or creates a default implementation for the given {@link StateDefinition}.
     * <p>
     * The method first checks if an existing state is registered for the provided state key.
     * If not, and the state key is among the {@code DEFAULT_IMPLEMENTATIONS}, it returns
     * a default instance depending on the state's structure (queue, singleton, or key-value).
     * <p>
     *
     * @param serviceName the name of the service with the state definition
     * @param definition the state definition containing the key and type information
     * @return the existing state object or a default implementation if available
     * @throws UnsupportedOperationException if the state key is not registered and no default exists
     */
    public Object lookup(final String serviceName, final StateDefinition<?, ?> definition) {
        final var stateId = definition.stateId();

        final var state = states.get(stateId);

        if (state != null) {
            return state;
        }

        final var stateKey = definition.stateKey();

        if (!DEFAULT_IMPLEMENTATIONS.contains(stateKey) && !stateKey.contains("I_UPGRADE")) {
            throw new UnsupportedOperationException("Unsupported state key: " + stateKey);
        }

        if (definition.queue()) {
            return new ConcurrentLinkedDeque<>();
        } else if (definition.singleton()) {
            return new DefaultSingleton(stateId);
        }

        return new MapReadableKVState<>(serviceName, stateId, new ConcurrentHashMap<>());
    }
}
