// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.google.common.collect.ImmutableMap;
import com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema;
import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.hedera.node.app.state.recordcache.schemas.V0540RecordCacheSchema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableKVState;
import jakarta.annotation.Nonnull;
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

@Named
public final class StateRegistry {
    private static final Set<String> DEFAULT_IMPLEMENTATIONS = Stream.of(
                    V0490TokenSchema.STAKING_INFO_KEY,
                    V0490FileSchema.UPGRADE_DATA_KEY,
                    V0490RecordCacheSchema.TXN_RECORD_QUEUE,
                    V0540RecordCacheSchema.TXN_RECEIPT_QUEUE,
                    V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY,
                    V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY,
                    V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY,
                    V0570ScheduleSchema.SCHEDULED_COUNTS_KEY,
                    V0570ScheduleSchema.SCHEDULED_ORDERS_KEY,
                    V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY,
                    V0570ScheduleSchema.SCHEDULED_USAGES_KEY,
                    V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY,
                    V0610TokenSchema.NODE_REWARDS_KEY)
            .collect(Collectors.toSet());

    private final ImmutableMap<String, Object> states;

    public StateRegistry(
            @Nonnull final Collection<ReadableKVState<?, ?>> keyValues,
            @Nonnull final Collection<SingletonState<?>> singletons) {

        this.states = ImmutableMap.<String, Object>builder()
                .putAll(keyValues.stream().collect(Collectors.toMap(ReadableKVState::getStateKey, kv -> kv)))
                .putAll(singletons.stream().collect(Collectors.toMap(SingletonState::getKey, s -> s)))
                .build();
    }

    /**
     * Looks up or creates a default implementation for the given {@link StateDefinition}.
     * <p>
     * The method first checks if an existing state is registered for the provided state key.
     * If not, and the state key is among the {@code DEFAULT_IMPLEMENTATIONS}, it returns
     * a default instance depending on the state's structure (queue, singleton, or key-value).
     * <p>
     * Special handling is applied for keys that start with {@code "UPGRADE_DATA"}:
     * these are normalized to {@link V0490FileSchema#UPGRADE_DATA_KEY} for validation
     * against default implementations, but the original key is still used when instantiating
     * the state object.
     *
     * @param definition the state definition containing the key and type information
     * @return the existing state object or a default implementation if available
     * @throws UnsupportedOperationException if the state key is not registered and no default exists
     */
    public Object lookup(final String serviceName, final StateDefinition<?, ?> definition) {
        final var stateKey = definition.stateKey();

        final var state = states.get(stateKey);

        if (state != null) {
            return state;
        }

        // var to handle keys that start with UPGRADE_DATA - need to be validated against default impl with `normalized`
        // upgrade data key that has no concrete shard/realm/num
        final String effectiveKey = stateKey.startsWith("UPGRADE_DATA") ? V0490FileSchema.UPGRADE_DATA_KEY : stateKey;

        if (!DEFAULT_IMPLEMENTATIONS.contains(effectiveKey)) {
            throw new UnsupportedOperationException("Unsupported state key: " + effectiveKey);
        }

        if (definition.queue()) {
            return new ConcurrentLinkedDeque<>();
        } else if (definition.singleton()) {
            return new DefaultSingleton(stateKey);
        }

        return new MapReadableKVState<>(serviceName, stateKey, new ConcurrentHashMap<>());
    }
}
