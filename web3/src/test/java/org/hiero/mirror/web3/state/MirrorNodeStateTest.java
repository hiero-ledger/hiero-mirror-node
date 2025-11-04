// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.spi.ReadableKVState;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
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
final class MirrorNodeStateTest {

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
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

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
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        assertThat(mirrorNodeState.getReadableStates("NEW").contains(FileReadableKVState.STATE_ID))
                .isFalse();
        final var newState = mirrorNodeState.addService(
                "NEW", new HashMap<>(Map.of(FileReadableKVState.STATE_ID, fileReadableKVState)));
        assertThat(newState.getReadableStates("NEW").contains(FileReadableKVState.STATE_ID))
                .isTrue();
    }

    @Test
    void testGetReadableStatesForFileService() {
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(FileReadableKVState.STATE_ID, fileReadableKVState)));
    }

    @Test
    void testGetReadableStatesForContractService() {
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        ContractBytecodeReadableKVState.STATE_ID,
                        contractBytecodeReadableKVState,
                        ContractStorageReadableKVState.STATE_ID,
                        contractStorageReadableKVState)));
    }

    @Test
    void testGetReadableStatesForTokenService() {
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(nftReadableKVState.getStateId()).thenReturn(NftReadableKVState.STATE_ID);
        when(tokenReadableKVState.getStateId()).thenReturn(TokenReadableKVState.STATE_ID);
        when(tokenRelationshipReadableKVState.getStateId()).thenReturn(TokenRelationshipReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);

        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(readableStates)
                .isEqualTo(new MapReadableStates(Map.of(
                        AccountReadableKVState.STATE_ID,
                        accountReadableKVState,
                        AirdropsReadableKVState.STATE_ID,
                        airdropsReadableKVState,
                        AliasesReadableKVState.STATE_ID,
                        aliasesReadableKVState,
                        NftReadableKVState.STATE_ID,
                        nftReadableKVState,
                        TokenReadableKVState.STATE_ID,
                        tokenReadableKVState,
                        TokenRelationshipReadableKVState.STATE_ID,
                        tokenRelationshipReadableKVState)));
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void testGetReadableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        final var id = 1;
        final var singleton = new DefaultSingleton(id);
        singleton.set(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of(id, singleton));
        final var readableStates = stateWithSingleton.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains(id)).isTrue();
        assertThat(readableStates.getSingleton(id).get()).isEqualTo(1L);
    }

    @Test
    void testGetReadableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(EntityIdService.NAME, Map.of(1, new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var readableStates = stateWithQueue.getReadableStates(EntityIdService.NAME);
        assertThat(readableStates.contains(1)).isTrue();
        assertThat(readableStates.getQueue(1).peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesForFileService() {
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                FileService.NAME,
                                FileReadableKVState.STATE_ID,
                                readableStates.get(FileReadableKVState.STATE_ID)))));
    }

    @Test
    void testGetWritableStatesForFileServiceWithListeners() {
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        final var writableStates = mirrorNodeState.getWritableStates(FileService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(FileService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        FileReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                FileService.NAME,
                                FileReadableKVState.STATE_ID,
                                readableStates.get(FileReadableKVState.STATE_ID)))));
    }

    @Test
    void testGetWritableStatesForContractService() {
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);

        final var writableStates = mirrorNodeState.getWritableStates(ContractService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(ContractService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        ContractBytecodeReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                ContractService.NAME,
                                ContractBytecodeReadableKVState.STATE_ID,
                                readableStates.get(ContractBytecodeReadableKVState.STATE_ID)),
                        ContractStorageReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                ContractService.NAME,
                                ContractStorageReadableKVState.STATE_ID,
                                readableStates.get(ContractStorageReadableKVState.STATE_ID)))));
    }

    @Test
    void testGetWritableStatesForTokenService() {
        when(accountReadableKVState.getStateId()).thenReturn(AccountReadableKVState.STATE_ID);
        when(airdropsReadableKVState.getStateId()).thenReturn(AirdropsReadableKVState.STATE_ID);
        when(aliasesReadableKVState.getStateId()).thenReturn(AliasesReadableKVState.STATE_ID);
        when(nftReadableKVState.getStateId()).thenReturn(NftReadableKVState.STATE_ID);
        when(tokenReadableKVState.getStateId()).thenReturn(TokenReadableKVState.STATE_ID);
        when(tokenRelationshipReadableKVState.getStateId()).thenReturn(TokenRelationshipReadableKVState.STATE_ID);
        when(contractBytecodeReadableKVState.getStateId()).thenReturn(ContractBytecodeReadableKVState.STATE_ID);
        when(contractStorageReadableKVState.getStateId()).thenReturn(ContractStorageReadableKVState.STATE_ID);
        when(fileReadableKVState.getStateId()).thenReturn(FileReadableKVState.STATE_ID);

        final var writableStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        final var readableStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        assertThat(writableStates)
                .isEqualTo(new MapWritableStates(Map.of(
                        AccountReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AccountReadableKVState.STATE_ID,
                                readableStates.get(AccountReadableKVState.STATE_ID)),
                        AirdropsReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AirdropsReadableKVState.STATE_ID,
                                readableStates.get(AirdropsReadableKVState.STATE_ID)),
                        AliasesReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                AliasesReadableKVState.STATE_ID,
                                readableStates.get(AliasesReadableKVState.STATE_ID)),
                        NftReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                NftReadableKVState.STATE_ID,
                                readableStates.get(NftReadableKVState.STATE_ID)),
                        TokenReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                TokenReadableKVState.STATE_ID,
                                readableStates.get(TokenReadableKVState.STATE_ID)),
                        TokenRelationshipReadableKVState.STATE_ID,
                        new MapWritableKVState<>(
                                TokenService.NAME,
                                TokenRelationshipReadableKVState.STATE_ID,
                                readableStates.get(TokenRelationshipReadableKVState.STATE_ID)))));
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }

    @Test
    void testGetWritableStatesWithSingleton() {
        final var stateWithSingleton = buildStateObject();
        final var id = 1;
        final var singleton = new DefaultSingleton(id);
        singleton.set(1L);
        stateWithSingleton.addService(EntityIdService.NAME, Map.of(id, singleton));
        final var writableStates = stateWithSingleton.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains(id)).isTrue();
        assertThat(writableStates.getSingleton(id).get()).isEqualTo(1L);
    }

    @Test
    void testGetWritableStatesWithQueue() {
        final var stateWithQueue = buildStateObject();
        stateWithQueue.addService(EntityIdService.NAME, Map.of(1, new ConcurrentLinkedDeque<>(Set.of("value"))));
        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains(1)).isTrue();
        assertThat(writableStates.getQueue(1).peek()).isEqualTo("value");
    }

    @Test
    void testGetWritableStatesWithQueueWithListeners() {
        final var stateWithQueue = buildStateObject();
        final var queue = new ConcurrentLinkedDeque<>(Set.of("value"));
        stateWithQueue.addService(EntityIdService.NAME, Map.of(1, queue));

        final var writableStates = stateWithQueue.getWritableStates(EntityIdService.NAME);
        assertThat(writableStates.contains(1)).isTrue();
        assertThat(writableStates.getQueue(1).peek()).isEqualTo("value");
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
        final Map<Integer, Object> fileStateData =
                new HashMap<>(Map.of(FileReadableKVState.STATE_ID, fileReadableKVState));
        final Map<Integer, Object> contractStateData = new HashMap<>(Map.of(
                ContractBytecodeReadableKVState.STATE_ID,
                contractBytecodeReadableKVState,
                ContractStorageReadableKVState.STATE_ID,
                contractStorageReadableKVState));
        final Map<Integer, Object> tokenStateData = new HashMap<>(Map.of(
                AccountReadableKVState.STATE_ID,
                accountReadableKVState,
                AirdropsReadableKVState.STATE_ID,
                airdropsReadableKVState,
                AliasesReadableKVState.STATE_ID,
                aliasesReadableKVState,
                NftReadableKVState.STATE_ID,
                nftReadableKVState,
                TokenReadableKVState.STATE_ID,
                tokenReadableKVState,
                TokenRelationshipReadableKVState.STATE_ID,
                tokenRelationshipReadableKVState));

        // Add service using the mock data source
        return buildStateObject()
                .addService(FileService.NAME, fileStateData)
                .addService(ContractService.NAME, contractStateData)
                .addService(TokenService.NAME, tokenStateData);
    }

    private MirrorNodeState buildStateObject() {
        return new MirrorNodeState(readableKVStates, servicesRegistry, mirrorNodeEvmProperties);
    }
}
