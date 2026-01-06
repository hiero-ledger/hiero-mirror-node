// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.BYTECODE_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V065ContractSchema.EVM_HOOK_STATES_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V065ContractSchema.LAMBDA_STORAGE_STATE_ID;
import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static com.hedera.node.app.service.entityid.impl.schemas.V0590EntityIdSchema.ENTITY_COUNTS_STATE_ID;
import static com.hedera.node.app.service.file.impl.schemas.V0490FileSchema.FILES_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_ID_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_COUNTS_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_USAGES_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ALIASES_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.NFTS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKEN_RELS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema.AIRDROPS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema.NODE_REWARDS_STATE_ID;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TRANSACTION_RECEIPTS_STATE_ID;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_ID;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_ID;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.entityid.EntityIdService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.state.State;
import com.swirlds.state.spi.EmptyWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.RequiredArgsConstructor;
import org.hiero.base.crypto.Hash;
import org.hiero.mirror.web3.state.core.FunctionReadableSingletonState;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.hiero.mirror.web3.state.core.ListReadableQueueState;
import org.hiero.mirror.web3.state.core.ListWritableQueueState;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.core.MapReadableStates;
import org.hiero.mirror.web3.state.core.MapWritableKVState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AirdropsReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.FileReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.NftReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ScheduleReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenRelationshipReadableKVState;
import org.hiero.mirror.web3.state.singleton.BlockInfoSingleton;
import org.hiero.mirror.web3.state.singleton.BlockStreamInfoSingleton;
import org.hiero.mirror.web3.state.singleton.CongestionLevelStartsSingleton;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.hiero.mirror.web3.state.singleton.EntityCountsSingleton;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.hiero.mirror.web3.state.singleton.MidnightRatesSingleton;
import org.hiero.mirror.web3.state.singleton.RunningHashesSingleton;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.hiero.mirror.web3.state.singleton.ThrottleUsageSingleton;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
@RequiredArgsConstructor
public class MirrorNodeState implements State {

    private final Map<String, ReadableStates> readableStates = new ConcurrentHashMap<>();
    private final Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();

    // Key is Service, value is Map of state name to state datasource
    private final Map<String, Map<Integer, Object>> states = new ConcurrentHashMap<>();

    // Singletons
    private final BlockInfoSingleton blockInfoSingleton;
    private final BlockStreamInfoSingleton blockStreamInfoSingleton;
    private final CongestionLevelStartsSingleton congestionLevelStartsSingleton;
    private final EntityIdSingleton entityIdSingleton;
    private final EntityCountsSingleton entityCountsSingleton;
    private final MidnightRatesSingleton midnightRatesSingleton;
    private final RunningHashesSingleton runningHashesSingleton;
    private final ThrottleUsageSingleton throttleUsageSingleton;

    // KV readable states
    private final AccountReadableKVState accountReadableKVState;
    private final AirdropsReadableKVState airdropsReadableKVState;
    private final AliasesReadableKVState aliasesReadableKVState;
    private final ContractBytecodeReadableKVState contractBytecodeReadableKVState;
    private final ContractStorageReadableKVState contractStorageReadableKVState;
    private final FileReadableKVState fileReadableKVState;
    private final NftReadableKVState nftReadableKVState;
    private final TokenReadableKVState tokenReadableKVState;
    private final ScheduleReadableKVState scheduleReadableKVState;
    private final TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    private final List<ReadableKVState> readableKVStates;

    @PostConstruct
    private void init() {
        registerBlockRecordServiceStates();
        registerBlockStreamServiceStates();
        registerCongestionThrottleServiceStates();
        registerContractServiceStates();
        registerEntityIdServiceStates();
        registerFeeServiceStates();
        registerFileServiceStates();
        registerRecordCacheServiceStates();
        registerScheduleServiceStates();
        registerTokenServiceStates();
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

    private void registerBlockRecordServiceStates() {
        final var blockRecordServiceStates = new ConcurrentHashMap<Integer, Object>();
        blockRecordServiceStates.put(RUNNING_HASHES_STATE_ID, runningHashesSingleton);
        blockRecordServiceStates.put(BLOCKS_STATE_ID, blockInfoSingleton);
        states.put(BlockRecordService.NAME, blockRecordServiceStates);
    }

    private void registerBlockStreamServiceStates() {
        states.put(
                BlockStreamService.NAME,
                new ConcurrentHashMap<>(Map.of(BLOCK_STREAM_INFO_STATE_ID, blockStreamInfoSingleton)));
    }

    private void registerCongestionThrottleServiceStates() {
        final var congestionThrottleServiceStates = new ConcurrentHashMap<Integer, Object>();
        congestionThrottleServiceStates.put(THROTTLE_USAGE_SNAPSHOTS_STATE_ID, throttleUsageSingleton);
        congestionThrottleServiceStates.put(CONGESTION_LEVEL_STARTS_STATE_ID, congestionLevelStartsSingleton);
        states.put(CongestionThrottleService.NAME, congestionThrottleServiceStates);
    }

    private void registerContractServiceStates() {
        final var contractServiceStates = new ConcurrentHashMap<Integer, Object>();
        contractServiceStates.put(BYTECODE_STATE_ID, contractBytecodeReadableKVState);
        contractServiceStates.put(STORAGE_STATE_ID, contractStorageReadableKVState);
        contractServiceStates.put(
                EVM_HOOK_STATES_STATE_ID, createMapReadableStateForId(ContractService.NAME, EVM_HOOK_STATES_STATE_ID));
        contractServiceStates.put(
                LAMBDA_STORAGE_STATE_ID, createMapReadableStateForId(ContractService.NAME, LAMBDA_STORAGE_STATE_ID));
        states.put(ContractService.NAME, contractServiceStates);
    }

    private void registerEntityIdServiceStates() {
        final var entityIdServiceStates = new ConcurrentHashMap<Integer, Object>();
        entityIdServiceStates.put(ENTITY_ID_STATE_ID, entityIdSingleton);
        entityIdServiceStates.put(ENTITY_COUNTS_STATE_ID, entityCountsSingleton);
        states.put(EntityIdService.NAME, entityIdServiceStates);
    }

    private void registerFeeServiceStates() {
        states.put(FeeService.NAME, new ConcurrentHashMap<>(Map.of(MIDNIGHT_RATES_STATE_ID, midnightRatesSingleton)));
    }

    private void registerFileServiceStates() {
        states.put(FileService.NAME, new ConcurrentHashMap<>(Map.of(FILES_STATE_ID, fileReadableKVState)));
    }

    private void registerRecordCacheServiceStates() {
        states.put(
                RecordCacheService.NAME,
                new ConcurrentHashMap<>(Map.of(TRANSACTION_RECEIPTS_STATE_ID, new ConcurrentLinkedDeque<>())));
    }

    private void registerScheduleServiceStates() {
        final var scheduleServiceStates = new ConcurrentHashMap<Integer, Object>();
        scheduleServiceStates.put(SCHEDULES_BY_ID_STATE_ID, scheduleReadableKVState);
        scheduleServiceStates.put(
                SCHEDULE_ID_BY_EQUALITY_STATE_ID,
                createMapReadableStateForId(ScheduleService.NAME, SCHEDULE_ID_BY_EQUALITY_STATE_ID));
        scheduleServiceStates.put(
                SCHEDULED_COUNTS_STATE_ID,
                createMapReadableStateForId(ScheduleService.NAME, SCHEDULED_COUNTS_STATE_ID));
        scheduleServiceStates.put(
                SCHEDULED_USAGES_STATE_ID,
                createMapReadableStateForId(ScheduleService.NAME, SCHEDULED_USAGES_STATE_ID));
        states.put(ScheduleService.NAME, scheduleServiceStates);
    }

    private void registerTokenServiceStates() {
        final var tokenServiceStates = new ConcurrentHashMap<Integer, Object>();
        tokenServiceStates.put(ACCOUNTS_STATE_ID, accountReadableKVState);
        tokenServiceStates.put(ALIASES_STATE_ID, aliasesReadableKVState);
        tokenServiceStates.put(TOKENS_STATE_ID, tokenReadableKVState);
        tokenServiceStates.put(NFTS_STATE_ID, nftReadableKVState);
        tokenServiceStates.put(TOKEN_RELS_STATE_ID, tokenRelationshipReadableKVState);
        tokenServiceStates.put(AIRDROPS_STATE_ID, airdropsReadableKVState);
        tokenServiceStates.put(
                STAKING_NETWORK_REWARDS_STATE_ID, new DefaultSingleton(STAKING_NETWORK_REWARDS_STATE_ID));
        tokenServiceStates.put(NODE_REWARDS_STATE_ID, new DefaultSingleton(NODE_REWARDS_STATE_ID));
        states.put(TokenService.NAME, tokenServiceStates);
    }

    private MapReadableKVState createMapReadableStateForId(final String serviceName, int id) {
        return new MapReadableKVState<>(serviceName, id, new HashMap<>());
    }
}
