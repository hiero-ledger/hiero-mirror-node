// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.StateChangeListener.StateType;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableStates;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.RecordFileRepository;
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
import org.hiero.mirror.web3.state.keyvalue.TokenReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenRelationshipReadableKVState;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class MirrorNodeStateTest {

    @InjectMocks
    private MirrorNodeState mirrorNodeState;

    @Mock
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private AirdropsReadableKVState airdropsReadableKVState;

    @Mock
    private AliasesReadableKVState aliasesReadableKVState;

    @Mock
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private FileReadableKVState fileReadableKVState;

    @Mock
    private NftReadableKVState nftReadableKVState;

    @Mock
    private TokenReadableKVState tokenReadableKVState;

    @Mock
    private TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    @Mock
    private ServicesRegistry servicesRegistry;

    @Mock
    private ServiceMigrator serviceMigrator;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private StateChangeListener listener;

    @Mock
    private RecordFileRepository recordFileRepository;

    @Mock
    private StoreMetricsServiceImpl storeMetricsService;

    @Mock
    private ConfigProviderImpl configProvider;

    private List<ReadableKVState> readableKVStates;

    @BeforeEach
    void setup() {
        readableKVStates = new LinkedList<>();
        readableKVStates.add(accountReadableKVState);
        readableKVStates.add(airdropsReadableKVState);
        readableKVStates.add(aliasesReadableKVState);
        readableKVStates.add(contractBytecodeReadableKVState);
        readableKVStates.add(contractStorageReadableKVState);
        readableKVStates.add(fileReadableKVState);
        readableKVStates.add(nftReadableKVState);
        readableKVStates.add(tokenReadableKVState);
        readableKVStates.add(tokenRelationshipReadableKVState);

        mirrorNodeState = initStateAfterMigration();
    }

    @Test
    void testAddService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        assertThat(mirrorNodeState.getReadableStates("NEW").contains(FileReadableKVState.KEY))
                .isFalse();
        final var newState =
                mirrorNodeState.addService("NEW", new HashMap<>(Map.of(FileReadableKVState.KEY, fileReadableKVState)));
        assertThat(newState.getReadableStates("NEW").contains(FileReadableKVState.KEY))
                .isTrue();
    }

    @Test
    void testRemoveService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var testStates = new HashMap<>(Map.of(
                ContractBytecodeReadableKVState.KEY,
                contractBytecodeReadableKVState,
                ContractStorageReadableKVState.KEY,
                contractStorageReadableKVState));
        final var newState = mirrorNodeState.addService("NEW", testStates);
        assertThat(newState.getReadableStates("NEW").contains(ContractBytecodeReadableKVState.KEY))
                .isTrue();
        assertThat(newState.getReadableStates("NEW").contains(ContractStorageReadableKVState.KEY))
                .isTrue();
        newState.removeServiceState("NEW", ContractBytecodeReadableKVState.KEY);
        assertThat(newState.getReadableStates("NEW").contains(ContractBytecodeReadableKVState.KEY))
                .isFalse();
        assertThat(newState.getReadableStates("NEW").contains(ContractStorageReadableKVState.KEY))
                .isTrue();
    }

    @Test
    void testGetReadableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(FileReadableKVState.KEY, fileReadableKVState)));
    }

    @Test
    void testGetReadableStatesForContractService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        ContractBytecodeReadableKVState.KEY,
                        contractBytecodeReadableKVState,
                        ContractStorageReadableKVState.KEY,
                        contractStorageReadableKVState)));
    }

    @Test
    void testGetReadableStatesForTokenService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(nftReadableKVState.getStateKey()).thenReturn(NftReadableKVState.KEY);
        when(tokenReadableKVState.getStateKey()).thenReturn(TokenReadableKVState.KEY);
        when(tokenRelationshipReadableKVState.getStateKey()).thenReturn(TokenRelationshipReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);

        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        AccountReadableKVState.KEY,
                        accountReadableKVState,
                        AirdropsReadableKVState.KEY,
                        airdropsReadableKVState,
                        AliasesReadableKVState.KEY,
                        aliasesReadableKVState,
                        NftReadableKVState.KEY,
                        nftReadableKVState,
                        TokenReadableKVState.KEY,
                        tokenReadableKVState,
                        TokenRelationshipReadableKVState.KEY,
                        tokenRelationshipReadableKVState)));
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void testGetReadableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        final var key = "EntityId";
        final var singleton = new DefaultSingleton(key);
        singleton.set(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of(key, singleton));
        final var readableStates = stateWithSingleton.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains(key)).isTrue();
        assertThat(readableStates.getSingleton(key).get()).isEqualTo(1L);
    }

    @Test
    void testGetReadableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(
                EntityIdService.NAME, Map.of("EntityId", new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var readableStates = stateWithQueue.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains("EntityId")).isTrue();
        assertThat(readableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesForFileService() {
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.KEY,
                        new MapWritableKVState<>(
                                FileService.NAME,
                                FileReadableKVState.KEY,
                                readableStates.get(FileReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForFileServiceWithListeners() {
        when(listener.stateTypes()).thenReturn(Set.of(StateType.MAP));
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        mirrorNodeState.registerCommitListener(listener);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.KEY,
                        new MapWritableKVState<>(
                                FileService.NAME,
                                FileReadableKVState.KEY,
                                readableStates.get(FileReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        ContractBytecodeReadableKVState.KEY,
                        new MapWritableKVState<>(
                                ContractService.NAME,
                                ContractBytecodeReadableKVState.KEY,
                                readableStates.get(ContractBytecodeReadableKVState.KEY)),
                        ContractStorageReadableKVState.KEY,
                        new MapWritableKVState<>(
                                ContractService.NAME,
                                ContractStorageReadableKVState.KEY,
                                readableStates.get(ContractStorageReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStatesForTokenService() {
        when(accountReadableKVState.getStateKey()).thenReturn(AccountReadableKVState.KEY);
        when(airdropsReadableKVState.getStateKey()).thenReturn(AirdropsReadableKVState.KEY);
        when(aliasesReadableKVState.getStateKey()).thenReturn(AliasesReadableKVState.KEY);
        when(nftReadableKVState.getStateKey()).thenReturn(NftReadableKVState.KEY);
        when(tokenReadableKVState.getStateKey()).thenReturn(TokenReadableKVState.KEY);
        when(tokenRelationshipReadableKVState.getStateKey()).thenReturn(TokenRelationshipReadableKVState.KEY);
        when(contractBytecodeReadableKVState.getStateKey()).thenReturn(ContractBytecodeReadableKVState.KEY);
        when(contractStorageReadableKVState.getStateKey()).thenReturn(ContractStorageReadableKVState.KEY);
        when(fileReadableKVState.getStateKey()).thenReturn(FileReadableKVState.KEY);

        final var writableStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        AccountReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AccountReadableKVState.KEY,
                                readableStates.get(AccountReadableKVState.KEY)),
                        AirdropsReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AirdropsReadableKVState.KEY,
                                readableStates.get(AirdropsReadableKVState.KEY)),
                        AliasesReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AliasesReadableKVState.KEY,
                                readableStates.get(AliasesReadableKVState.KEY)),
                        NftReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME, NftReadableKVState.KEY, readableStates.get(NftReadableKVState.KEY)),
                        TokenReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                TokenReadableKVState.KEY,
                                readableStates.get(TokenReadableKVState.KEY)),
                        TokenRelationshipReadableKVState.KEY,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                TokenRelationshipReadableKVState.KEY,
                                readableStates.get(TokenRelationshipReadableKVState.KEY)))));
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }

    @Test
    void testGetWritableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        final var key = "EntityId";
        final var singleton = new DefaultSingleton(key);
        singleton.set(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of(key, singleton));
        final var writableStates = stateWithSingleton.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains(key)).isTrue();
        assertThat(writableStates.getSingleton(key).get()).isEqualTo(1L);
    }

    @Test
    void testGetWritableStatesWithSingletonWithListeners() {
        final var stateWithSingleton = buildStateObject();
        final var key = "EntityId";
        final var singleton = new DefaultSingleton(key);
        singleton.set(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of(key, singleton));
        when(listener.stateTypes()).thenReturn(Set.of(StateType.SINGLETON));
        stateWithSingleton.registerCommitListener(listener);

        final var writableStates = stateWithSingleton.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getSingleton("EntityId").get()).isEqualTo(1L);
    }

    @Test
    void testGetWritableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(
                EntityIdService.NAME, Map.of("EntityId", new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesWithQueueWithListeners() {
        final var stateWithQueue = buildStateObject();
        final var queue = new ConcurrentLinkedDeque<>(Set.of("value"));
        stateWithQueue.addService(EntityIdService.NAME, Map.of("EntityId", queue));
        when(listener.stateTypes()).thenReturn(Set.of(StateType.QUEUE));
        stateWithQueue.registerCommitListener(listener);

        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains("EntityId")).isTrue();
        assertThat(writableStates.getQueue("EntityId").peek()).isEqualTo("value");
    }

    @Test
    void testRegisterCommitListener() {
        final var state1 = buildStateObject();
        final var state2 = buildStateObject();
        assertThat(state1).isEqualTo(state2);
        state1.registerCommitListener(listener);
        assertThat(state1).isNotEqualTo(state2);
    }

    @Test
    void testUnregisterCommitListener() {
        final var state1 = buildStateObject();
        final var state2 = buildStateObject();
        assertThat(state1).isEqualTo(state2);
        state1.registerCommitListener(listener);
        assertThat(state1).isNotEqualTo(state2);
        state1.unregisterCommitListener(listener);
        assertThat(state1).isEqualTo(state2);
    }

    @Test
    void testCommit() {
        final var state = buildStateObject();
        final var mockMapWritableState = mock(MapWritableStates.class);
        Map<String, WritableStates> writableStates = new ConcurrentHashMap<>();
        writableStates.put(FileService.NAME, mockMapWritableState);
        state.setWritableStates(writableStates);
        state.commit();
        verify(mockMapWritableState, times(1)).commit();
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(mirrorNodeState).isEqualTo(mirrorNodeState);
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mirrorNodeState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mirrorNodeState).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        final var other = initStateAfterMigration();
        assertThat(mirrorNodeState).isEqualTo(other);
    }

    @Test
    void testHashCode() {
        final var other = initStateAfterMigration();
        assertThat(mirrorNodeState).hasSameHashCodeAs(other);
    }

    private MirrorNodeState initStateAfterMigration() {
        final Map<String, Object> fileStateData = new HashMap<>(Map.of(FileReadableKVState.KEY, fileReadableKVState));
        final Map<String, Object> contractStateData = new HashMap<>(Map.of(
                ContractBytecodeReadableKVState.KEY,
                contractBytecodeReadableKVState,
                ContractStorageReadableKVState.KEY,
                contractStorageReadableKVState));
        final Map<String, Object> tokenStateData = new HashMap<>(Map.of(
                AccountReadableKVState.KEY,
                accountReadableKVState,
                AirdropsReadableKVState.KEY,
                airdropsReadableKVState,
                AliasesReadableKVState.KEY,
                aliasesReadableKVState,
                NftReadableKVState.KEY,
                nftReadableKVState,
                TokenReadableKVState.KEY,
                tokenReadableKVState,
                TokenRelationshipReadableKVState.KEY,
                tokenRelationshipReadableKVState));

        // Add service using the mock data source
        return buildStateObject()
                .addService(FileService.NAME, fileStateData)
                .addService(ContractService.NAME, contractStateData)
                .addService(TokenService.NAME, tokenStateData);
    }

    private MirrorNodeState buildStateObject() {
        return new MirrorNodeState(
                readableKVStates,
                servicesRegistry,
                serviceMigrator,
                startupNetworks,
                mirrorNodeEvmProperties,
                recordFileRepository,
                storeMetricsService,
                configProvider);
    }
}
