// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.FileReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.NftReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.TokenReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.TokenRelationshipReadableKVState;
import com.hedera.mirror.web3.state.singleton.BlockInfoSingleton;
import com.hedera.mirror.web3.state.singleton.CongestionLevelStartsSingleton;
import com.hedera.mirror.web3.state.singleton.EntityIdSingleton;
import com.hedera.mirror.web3.state.singleton.MidnightRatesSingleton;
import com.hedera.mirror.web3.state.singleton.RunningHashesSingleton;
import com.hedera.mirror.web3.state.singleton.ThrottleUsageSingleton;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.state.lifecycle.Service;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class MirrorNodeStateIntegrationTest extends Web3IntegrationTest {

    private final MirrorNodeState mirrorNodeState;
    private final ServicesRegistry servicesRegistry;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Test
    void verifyMirrorNodeStateHasRegisteredServices() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            return;
        }

        Set<Class<? extends Service>> expectedServices = new HashSet<>(List.of(
                EntityIdService.class,
                TokenServiceImpl.class,
                FileServiceImpl.class,
                ContractServiceImpl.class,
                BlockRecordService.class,
                FeeService.class,
                CongestionThrottleService.class,
                RecordCacheService.class,
                ScheduleServiceImpl.class));

        final var registeredServices = servicesRegistry.registrations();
        assertThat(registeredServices).hasSameSizeAs(expectedServices);

        for (var expectedService : expectedServices) {
            assertThat(registeredServices.stream()
                            .anyMatch(registration ->
                                    registration.service().getClass().equals(expectedService)))
                    .isTrue();
        }
    }

    @Test
    void verifyServicesHaveAssignedDataSources() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            return;
        }

        final var states = mirrorNodeState.getStates();

        // BlockRecordService
        Map<String, Class<?>> blockRecordServiceDataSources = Map.of(
                "BLOCKS", BlockInfoSingleton.class,
                "RUNNING_HASHES", RunningHashesSingleton.class);
        verifyServiceDataSources(states, BlockRecordService.NAME, blockRecordServiceDataSources);

        // FileService
        Map<String, Class<?>> fileServiceDataSources = Map.of(FileReadableKVState.KEY, Map.class);
        verifyServiceDataSources(states, FileService.NAME, fileServiceDataSources);

        // CongestionThrottleService
        Map<String, Class<?>> congestionThrottleServiceDataSources = Map.of(
                "THROTTLE_USAGE_SNAPSHOTS", ThrottleUsageSingleton.class,
                "CONGESTION_LEVEL_STARTS", CongestionLevelStartsSingleton.class);
        verifyServiceDataSources(states, CongestionThrottleService.NAME, congestionThrottleServiceDataSources);

        // FeeService
        Map<String, Class<?>> feeServiceDataSources = Map.of("MIDNIGHT_RATES", MidnightRatesSingleton.class);
        verifyServiceDataSources(states, FeeService.NAME, feeServiceDataSources);

        // ContractService
        Map<String, Class<?>> contractServiceDataSources = Map.of(
                ContractBytecodeReadableKVState.KEY, Map.class,
                ContractStorageReadableKVState.KEY, Map.class);
        verifyServiceDataSources(states, ContractService.NAME, contractServiceDataSources);

        // RecordCacheService
        Map<String, Class<?>> recordCacheServiceDataSources = Map.of("TransactionReceiptQueue", Deque.class);
        verifyServiceDataSources(states, RecordCacheService.NAME, recordCacheServiceDataSources);

        // EntityIdService
        Map<String, Class<?>> entityIdServiceDataSources = Map.of("ENTITY_ID", EntityIdSingleton.class);
        verifyServiceDataSources(states, EntityIdService.NAME, entityIdServiceDataSources);

        // TokenService
        Map<String, Class<?>> tokenServiceDataSources = Map.of(
                AccountReadableKVState.KEY,
                Map.class,
                "PENDING_AIRDROPS",
                Map.class,
                AliasesReadableKVState.KEY,
                Map.class,
                NftReadableKVState.KEY,
                Map.class,
                TokenReadableKVState.KEY,
                Map.class,
                TokenRelationshipReadableKVState.KEY,
                Map.class,
                "STAKING_NETWORK_REWARDS",
                AtomicReference.class);
        verifyServiceDataSources(states, TokenService.NAME, tokenServiceDataSources);
    }

    private void verifyServiceDataSources(
            Map<String, Map<String, Object>> states, String serviceName, Map<String, Class<?>> expectedDataSources) {
        final var serviceState = states.get(serviceName);
        assertThat(serviceState).isNotNull();
        expectedDataSources.forEach((key, type) -> {
            assertThat(serviceState).containsKey(key);
            assertThat(serviceState.get(key)).isInstanceOf(type);
        });
    }
}
