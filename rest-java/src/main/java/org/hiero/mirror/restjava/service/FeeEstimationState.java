// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.spi.ReadableKVStateBase;
import com.swirlds.state.spi.ReadableQueueStateBase;
import com.swirlds.state.spi.ReadableSingletonStateBase;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.base.crypto.Hash;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("NullableProblems")
final class FeeEstimationState implements State {

    private final Map<String, Map<Integer, Object>> states = new ConcurrentHashMap<>();
    private final Map<String, ReadableStates> readableStatesCache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    FeeEstimationState(Bytes simpleFeeBytes) {
        var midnightRates = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .centEquiv(12)
                        .hbarEquiv(1)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(Long.MAX_VALUE))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .centEquiv(15)
                        .hbarEquiv(1)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(Long.MAX_VALUE))
                        .build())
                .build();
        addSingleton(FeeService.NAME, V0490FeeSchema.MIDNIGHT_RATES_STATE_ID, midnightRates);

        var addressBook = NodeAddressBook.newBuilder()
                .nodeAddress(NodeAddress.newBuilder()
                        .nodeId(0)
                        .nodeAccountId(AccountID.newBuilder().accountNum(3).build())
                        .stake(1)
                        .build())
                .build();
        var bytes = NodeAddressBook.PROTOBUF.toBytes(addressBook);
        var file = File.newBuilder().contents(bytes).build();
        var fileId = FileID.newBuilder().fileNum(102).build();
        var files = (Map<FileID, File>) states.computeIfAbsent(FileService.NAME, _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(V0490FileSchema.FILES_STATE_ID, _ -> new ConcurrentHashMap<>());
        files.put(fileId, file);
        files.put(
                FileID.newBuilder().fileNum(113).build(),
                File.newBuilder().contents(simpleFeeBytes).build());

        addSingleton(
                CongestionThrottleService.NAME,
                V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_ID,
                ThrottleUsageSnapshots.DEFAULT);
        addSingleton(
                CongestionThrottleService.NAME,
                V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_ID,
                CongestionLevelStarts.DEFAULT);
    }

    private void addSingleton(String serviceName, int stateId, Object value) {
        states.computeIfAbsent(serviceName, _ -> new ConcurrentHashMap<>()).put(stateId, new AtomicReference<>(value));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ReadableStates getReadableStates(String serviceName) {
        return readableStatesCache.computeIfAbsent(serviceName, s -> {
            var serviceStates = states.get(s);
            if (serviceStates == null) {
                return new LenientReadableStates(Map.of());
            }
            Map<Integer, Object> wrapped = new HashMap<>();
            for (var entry : serviceStates.entrySet()) {
                int stateId = entry.getKey();
                Object state = entry.getValue();
                if (state instanceof Map map) {
                    wrapped.put(stateId, new InMemoryReadableKVState<>(stateId, map));
                } else if (state instanceof AtomicReference ref) {
                    wrapped.put(stateId, new InMemoryReadableSingletonState<>(stateId, ref));
                }
            }
            return new LenientReadableStates(wrapped);
        });
    }

    @Override
    public WritableStates getWritableStates(String serviceName) {
        throw new UnsupportedOperationException("Fee estimation is read-only");
    }

    @Override
    public void registerCommitListener(StateChangeListener listener) {
        // No-op
    }

    @Override
    public void unregisterCommitListener(StateChangeListener listener) {
        // No-op
    }

    @Override
    public void setHash(Hash hash) {
        // No-op
    }

    private record LenientReadableStates(Map<Integer, Object> stateMap) implements ReadableStates {

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> InMemoryReadableKVState<K, V> get(int stateId) {
            var state = stateMap.get(stateId);
            if (state != null) {
                return (InMemoryReadableKVState<K, V>) state;
            }
            return new InMemoryReadableKVState<>(stateId, Map.of());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReadableSingletonStateBase<T> getSingleton(int stateId) {
            var state = stateMap.get(stateId);
            if (state != null) {
                return (ReadableSingletonStateBase<T>) state;
            }
            return new InMemoryReadableSingletonState<>(stateId, new AtomicReference<>());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> InMemoryReadableQueueState<E> getQueue(int stateId) {
            var state = stateMap.get(stateId);
            if (state != null) {
                return (InMemoryReadableQueueState<E>) state;
            }
            return new InMemoryReadableQueueState<>(stateId, new LinkedList<>());
        }

        @Override
        public boolean contains(int stateId) {
            return stateMap.containsKey(stateId);
        }

        @Override
        public Set<Integer> stateIds() {
            return stateMap.keySet();
        }
    }

    private static final class InMemoryReadableKVState<K, V> extends ReadableKVStateBase<K, V> {
        private final Map<K, V> backingMap;

        InMemoryReadableKVState(int stateId, Map<K, V> backingMap) {
            super(stateId, "state." + stateId);
            this.backingMap = backingMap;
        }

        @Override
        protected @Nullable V readFromDataSource(K key) {
            return backingMap.get(key);
        }

        @Override
        public long size() {
            return backingMap.size();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private static final class InMemoryReadableSingletonState<T> extends ReadableSingletonStateBase<T> {
        private final AtomicReference<T> backingRef;

        InMemoryReadableSingletonState(int stateId, AtomicReference<T> backingRef) {
            super(stateId, "state." + stateId);
            this.backingRef = backingRef;
        }

        @Override
        protected @Nullable T readFromDataSource() {
            return backingRef.get();
        }
    }

    private static final class InMemoryReadableQueueState<E> extends ReadableQueueStateBase<E> {
        private final Queue<E> backingQueue;

        InMemoryReadableQueueState(int stateId, Queue<E> backingQueue) {
            super(stateId, "state." + stateId);
            this.backingQueue = backingQueue;
        }

        @Override
        protected @Nullable E peekOnDataSource() {
            return backingQueue.peek();
        }

        @Override
        protected Iterator<E> iterateOnDataSource() {
            return Collections.unmodifiableCollection(backingQueue).iterator();
        }
    }
}
