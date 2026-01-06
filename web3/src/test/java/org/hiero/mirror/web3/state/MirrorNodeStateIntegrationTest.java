// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_ID_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_COUNTS_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_USAGES_STATE_ID;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
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
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AirdropsReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.FileReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.NftReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenRelationshipReadableKVState;
import org.hiero.mirror.web3.state.singleton.BlockInfoSingleton;
import org.hiero.mirror.web3.state.singleton.BlockStreamInfoSingleton;
import org.hiero.mirror.web3.state.singleton.CongestionLevelStartsSingleton;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.hiero.mirror.web3.state.singleton.MidnightRatesSingleton;
import org.hiero.mirror.web3.state.singleton.RunningHashesSingleton;
import org.hiero.mirror.web3.state.singleton.ThrottleUsageSingleton;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class MirrorNodeStateIntegrationTest extends Web3IntegrationTest {

    private final MirrorNodeState mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Test
    void verifyServicesHaveAssignedDataSources() {
        final var states = mirrorNodeState.getStates();

        // BlockRecordService
        Map<Integer, Class<?>> blockRecordServiceDataSources = Map.of(
                BLOCKS_STATE_ID, BlockInfoSingleton.class,
                RUNNING_HASHES_STATE_ID, RunningHashesSingleton.class);
        verifyServiceDataSources(states, BlockRecordService.NAME, blockRecordServiceDataSources);

        // BlockStreamService
        Map<Integer, Class<?>> blockStreamServiceDataSources =
                Map.of(BLOCK_STREAM_INFO_STATE_ID, BlockStreamInfoSingleton.class);
        verifyServiceDataSources(states, BlockStreamService.NAME, blockStreamServiceDataSources);

        // CongestionThrottleService
        Map<Integer, Class<?>> congestionThrottleServiceDataSources = Map.of(
                THROTTLE_USAGE_SNAPSHOTS_STATE_ID, ThrottleUsageSingleton.class,
                CONGESTION_LEVEL_STARTS_STATE_ID, CongestionLevelStartsSingleton.class);
        verifyServiceDataSources(states, CongestionThrottleService.NAME, congestionThrottleServiceDataSources);

        // ContractService
        Map<Integer, Class<?>> contractServiceDataSources = Map.of(
                ContractBytecodeReadableKVState.STATE_ID, ReadableKVState.class,
                ContractStorageReadableKVState.STATE_ID, ReadableKVState.class);
        verifyServiceDataSources(states, ContractService.NAME, contractServiceDataSources);

        // EntityIdService
        Map<Integer, Class<?>> entityIdServiceDataSources = Map.of(ENTITY_ID_STATE_ID, EntityIdSingleton.class);
        verifyServiceDataSources(states, EntityIdService.NAME, entityIdServiceDataSources);

        // FeeService
        Map<Integer, Class<?>> feeServiceDataSources = Map.of(MIDNIGHT_RATES_STATE_ID, MidnightRatesSingleton.class);
        verifyServiceDataSources(states, FeeService.NAME, feeServiceDataSources);

        // FileService
        Map<Integer, Class<?>> fileServiceDataSources = Map.of(FileReadableKVState.STATE_ID, ReadableKVState.class);
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
                AccountReadableKVState.STATE_ID,
                ReadableKVState.class,
                AirdropsReadableKVState.STATE_ID,
                ReadableKVState.class,
                AliasesReadableKVState.STATE_ID,
                ReadableKVState.class,
                NftReadableKVState.STATE_ID,
                ReadableKVState.class,
                TokenReadableKVState.STATE_ID,
                ReadableKVState.class,
                TokenRelationshipReadableKVState.STATE_ID,
                ReadableKVState.class,
                STAKING_NETWORK_REWARDS_STATE_ID,
                AtomicReference.class);
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
