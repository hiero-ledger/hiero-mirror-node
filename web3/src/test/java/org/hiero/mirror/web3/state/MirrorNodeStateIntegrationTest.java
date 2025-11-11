// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.RUNNING_HASHES_STATE_ID;
import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TRANSACTION_RECEIPTS_STATE_ID;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_ID;
import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.entityid.EntityIdService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.spi.ReadableKVState;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.state.components.ServicesRegistryImpl;
import org.hiero.mirror.web3.state.components.entity.EntityIdServiceImpl;
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
import org.hiero.mirror.web3.state.singleton.CongestionLevelStartsSingleton;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.hiero.mirror.web3.state.singleton.MidnightRatesSingleton;
import org.hiero.mirror.web3.state.singleton.RunningHashesSingleton;
import org.hiero.mirror.web3.state.singleton.ThrottleUsageSingleton;
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

        verifyRegisteredServices();
    }

    @Test
    void verifyServicesHaveAssignedDataSources() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            return;
        }

        final var states = mirrorNodeState.getStates();

        // BlockRecordService
        Map<Integer, Class<?>> blockRecordServiceDataSources = Map.of(
                BLOCKS_STATE_ID, BlockInfoSingleton.class,
                RUNNING_HASHES_STATE_ID, RunningHashesSingleton.class);
        verifyServiceDataSources(states, BlockRecordService.NAME, blockRecordServiceDataSources);

        // FileService
        Map<Integer, Class<?>> fileServiceDataSources = Map.of(FileReadableKVState.STATE_ID, ReadableKVState.class);
        verifyServiceDataSources(states, FileService.NAME, fileServiceDataSources);

        // CongestionThrottleService
        Map<Integer, Class<?>> congestionThrottleServiceDataSources = Map.of(
                THROTTLE_USAGE_SNAPSHOTS_STATE_ID, ThrottleUsageSingleton.class,
                CONGESTION_LEVEL_STARTS_STATE_ID, CongestionLevelStartsSingleton.class);
        verifyServiceDataSources(states, CongestionThrottleService.NAME, congestionThrottleServiceDataSources);

        // FeeService
        Map<Integer, Class<?>> feeServiceDataSources = Map.of(MIDNIGHT_RATES_STATE_ID, MidnightRatesSingleton.class);
        verifyServiceDataSources(states, FeeService.NAME, feeServiceDataSources);

        // ContractService
        Map<Integer, Class<?>> contractServiceDataSources = Map.of(
                ContractBytecodeReadableKVState.STATE_ID, ReadableKVState.class,
                ContractStorageReadableKVState.STATE_ID, ReadableKVState.class);
        verifyServiceDataSources(states, ContractService.NAME, contractServiceDataSources);

        // RecordCacheService
        Map<Integer, Class<?>> recordCacheServiceDataSources = Map.of(TRANSACTION_RECEIPTS_STATE_ID, Deque.class);
        verifyServiceDataSources(states, RecordCacheService.NAME, recordCacheServiceDataSources);

        // EntityIdService
        Map<Integer, Class<?>> entityIdServiceDataSources = Map.of(ENTITY_ID_STATE_ID, EntityIdSingleton.class);
        verifyServiceDataSources(states, EntityIdService.NAME, entityIdServiceDataSources);

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

    @Test
    void verifyMirrorNodeNonGenesisStateHasRegisteredServices() throws Exception {
        boolean initialModularized = mirrorNodeEvmProperties.isModularizedServices();
        if (!initialModularized) {
            mirrorNodeEvmProperties.setModularizedServices(true);
        }

        try {
            // Prepare for the test
            prepareNonGenesisTestEnvironment();

            // Execute the init method to initialize the state
            initializeMirrorNodeState();

            // Verify the expected services are registered
            verifyRegisteredServices();
        } finally {
            // Reset properties to their initial state
            resetProperties(initialModularized);
        }
    }

    private void verifyRegisteredServices() {
        Set<Class<? extends Service>> expectedServices = new HashSet<>(List.of(
                EntityIdServiceImpl.class,
                TokenServiceImpl.class,
                FileServiceImpl.class,
                ContractServiceImpl.class,
                BlockRecordService.class,
                FeeService.class,
                CongestionThrottleService.class,
                RecordCacheService.class,
                ScheduleServiceImpl.class,
                BlockStreamService.class));

        final var registeredServices = servicesRegistry.registrations();

        // Verify the servicesRegistry has the expected services registered
        assertThat(registeredServices).hasSameSizeAs(expectedServices);

        for (var expectedService : expectedServices) {
            assertThat(registeredServices.stream()
                            .anyMatch(registration ->
                                    registration.service().getClass().equals(expectedService)))
                    .isTrue();
        }
    }

    private void prepareNonGenesisTestEnvironment() throws NoSuchFieldException, IllegalAccessException {
        // Persist a record file to ensure we can start in non-genesis mode
        domainBuilder.recordFile().persist();

        // Clear existing registrations from servicesRegistry
        clearServiceRegistryEntries();
    }

    private void clearServiceRegistryEntries() throws NoSuchFieldException, IllegalAccessException {
        Field entriesField = ServicesRegistryImpl.class.getDeclaredField("entries");
        entriesField.setAccessible(true);

        SortedSet<?> entriesSet = (SortedSet<?>) entriesField.get(servicesRegistry);
        entriesSet.clear();
    }

    private void initializeMirrorNodeState() throws IllegalAccessException, InvocationTargetException {
        // Find and invoke the @PostConstruct method
        Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

        postConstructMethod.setAccessible(true);
        postConstructMethod.invoke(mirrorNodeState);
    }

    private void resetProperties(boolean initialModularized) {
        mirrorNodeEvmProperties.setModularizedServices(initialModularized);
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
