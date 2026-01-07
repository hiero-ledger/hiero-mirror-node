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
import static org.assertj.core.api.Assertions.assertThat;

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
import com.swirlds.state.spi.ReadableKVState;
import java.util.Deque;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class MirrorNodeStateIntegrationTest extends Web3IntegrationTest {

    private final MirrorNodeState mirrorNodeState;

    @Test
    void initPopulatesAllServices() {
        var states = mirrorNodeState.getStates();
        assertThat(states)
                .containsKeys(
                        BlockRecordService.NAME,
                        BlockStreamService.NAME,
                        CongestionThrottleService.NAME,
                        ContractService.NAME,
                        EntityIdService.NAME,
                        FeeService.NAME,
                        FileService.NAME,
                        RecordCacheService.NAME,
                        ScheduleService.NAME,
                        TokenService.NAME);
    }

    @Test
    void verifyServicesHaveAssignedDataSources() {
        final var states = mirrorNodeState.getStates();

        // BlockRecordService
        Map<Integer, Class<?>> blockRecordServiceDataSources = Map.of(
                BLOCKS_STATE_ID, SingletonState.class,
                RUNNING_HASHES_STATE_ID, SingletonState.class);
        verifyServiceDataSources(states, BlockRecordService.NAME, blockRecordServiceDataSources);

        // BlockStreamService
        Map<Integer, Class<?>> blockStreamServiceDataSources = Map.of(BLOCK_STREAM_INFO_STATE_ID, SingletonState.class);
        verifyServiceDataSources(states, BlockStreamService.NAME, blockStreamServiceDataSources);

        // CongestionThrottleService
        Map<Integer, Class<?>> congestionThrottleServiceDataSources = Map.of(
                THROTTLE_USAGE_SNAPSHOTS_STATE_ID, SingletonState.class,
                CONGESTION_LEVEL_STARTS_STATE_ID, SingletonState.class);
        verifyServiceDataSources(states, CongestionThrottleService.NAME, congestionThrottleServiceDataSources);

        // ContractService
        Map<Integer, Class<?>> contractServiceDataSources = Map.of(
                BYTECODE_STATE_ID, ReadableKVState.class,
                STORAGE_STATE_ID, ReadableKVState.class,
                EVM_HOOK_STATES_STATE_ID, ReadableKVState.class,
                LAMBDA_STORAGE_STATE_ID, ReadableKVState.class);
        verifyServiceDataSources(states, ContractService.NAME, contractServiceDataSources);

        // EntityIdService
        Map<Integer, Class<?>> entityIdServiceDataSources = Map.of(
                ENTITY_ID_STATE_ID, SingletonState.class,
                ENTITY_COUNTS_STATE_ID, SingletonState.class);
        verifyServiceDataSources(states, EntityIdService.NAME, entityIdServiceDataSources);

        // FeeService
        Map<Integer, Class<?>> feeServiceDataSources = Map.of(MIDNIGHT_RATES_STATE_ID, SingletonState.class);
        verifyServiceDataSources(states, FeeService.NAME, feeServiceDataSources);

        // FileService
        Map<Integer, Class<?>> fileServiceDataSources = Map.of(FILES_STATE_ID, ReadableKVState.class);
        verifyServiceDataSources(states, FileService.NAME, fileServiceDataSources);

        // RecordCacheService
        Map<Integer, Class<?>> recordCacheServiceDataSources = Map.of(TRANSACTION_RECEIPTS_STATE_ID, Deque.class);
        verifyServiceDataSources(states, RecordCacheService.NAME, recordCacheServiceDataSources);

        // ScheduleService
        Map<Integer, Class<?>> scheduleServiceDataSources = Map.of(
                SCHEDULES_BY_ID_STATE_ID,
                ReadableKVState.class,
                SCHEDULE_ID_BY_EQUALITY_STATE_ID,
                MapReadableKVState.class,
                SCHEDULED_COUNTS_STATE_ID,
                MapReadableKVState.class,
                SCHEDULED_USAGES_STATE_ID,
                MapReadableKVState.class);
        verifyServiceDataSources(states, ScheduleService.NAME, scheduleServiceDataSources);

        // TokenService
        Map<Integer, Class<?>> tokenServiceDataSources = Map.of(
                ACCOUNTS_STATE_ID,
                ReadableKVState.class,
                AIRDROPS_STATE_ID,
                ReadableKVState.class,
                ALIASES_STATE_ID,
                ReadableKVState.class,
                NFTS_STATE_ID,
                ReadableKVState.class,
                TOKENS_STATE_ID,
                ReadableKVState.class,
                TOKEN_RELS_STATE_ID,
                ReadableKVState.class,
                STAKING_NETWORK_REWARDS_STATE_ID,
                DefaultSingleton.class,
                NODE_REWARDS_STATE_ID,
                DefaultSingleton.class);
        verifyServiceDataSources(states, TokenService.NAME, tokenServiceDataSources);
    }

    private void verifyServiceDataSources(
            Map<String, Map<Integer, Object>> states, String serviceName, Map<Integer, Class<?>> expectedDataSources) {
        final var serviceState = states.get(serviceName);
        assertThat(serviceState).isNotNull();
        expectedDataSources.forEach((key, type) -> {
            assertThat(serviceState).containsKey(key);
            assertThat(serviceState.get(key)).isInstanceOf(type);
        });
    }
}
