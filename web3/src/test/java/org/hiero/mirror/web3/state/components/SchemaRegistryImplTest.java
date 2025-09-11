// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static java.util.Collections.EMPTY_MAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.state.merkle.SchemaApplicationType;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableStates;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.hiero.mirror.web3.state.singleton.DefaultSingleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaRegistryImplTest {

    private static final String UNSUPPORTED_STATE_KEY_MESSAGE = "Unsupported state key: ";
    private final String serviceName = "testService";
    private final SemanticVersion previousVersion = new SemanticVersion(0, 46, 0, "", "");

    @Mock
    private MirrorNodeState mirrorNodeState;

    @Mock
    private Schema schema;

    @Mock
    private MapWritableStates writableStates;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private SchemaApplications schemaApplications;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private Codec<String> mockCodec;

    @Mock
    private StateRegistry stateRegistry;

    private Configuration config;
    private SchemaRegistryImpl schemaRegistry;

    @BeforeEach
    void initialize() {
        schemaRegistry = new SchemaRegistryImpl(schemaApplications, stateRegistry);
        config = new ConfigProviderImpl().getConfiguration();
    }

    @Test
    void testRegisterSchema() {
        schemaRegistry.register(schema);
        SortedSet<Schema> schemas = schemaRegistry.getSchemas();
        assertThat(schemas).contains(schema);
    }

    @Test
    void testMigrateWithSingleSchema() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.noneOf(SchemaApplicationType.class));

        schemaRegistry.register(schema);

        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, config, config, new HashMap<>(), startupNetworks);
        verify(mirrorNodeState, times(1)).getWritableStates(serviceName);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testMigrateWithMigrations() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.MIGRATION));
        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, config, config, new HashMap<>(), startupNetworks);

        verify(schema).migrate(any());
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testMigrateWithStateDefinitions() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.STATE_DEFINITIONS, SchemaApplicationType.MIGRATION));

        var stateDefinitionSingleton = new StateDefinition<>(
                V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY, mockCodec, mockCodec, 123, false, true, false);
        var stateDefinitionQueue =
                new StateDefinition<>(V0490TokenSchema.STAKING_INFO_KEY, mockCodec, mockCodec, 123, false, false, true);
        var stateDefinition =
                new StateDefinition<>(AccountReadableKVState.KEY, mockCodec, mockCodec, 123, true, false, false);

        when(schema.statesToCreate(config))
                .thenReturn(Set.of(stateDefinitionSingleton, stateDefinitionQueue, stateDefinition));

        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenReturn(new DefaultSingleton(V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY));
        when(stateRegistry.lookup(serviceName, stateDefinitionQueue)).thenReturn(new ConcurrentLinkedDeque<>());
        when(stateRegistry.lookup(serviceName, stateDefinition))
                .thenReturn(new MapReadableKVState<>(
                        TokenService.NAME, AccountReadableKVState.KEY, new ConcurrentHashMap<>()));

        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, config, config, new HashMap<>(), startupNetworks);
        verify(mirrorNodeState, times(1)).getWritableStates(serviceName);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
        verify(schema).migrate(any());
        verify(writableStates, times(1)).commit();
        verify(mirrorNodeState, times(1)).addService(any(), any());
    }

    @Test
    void testMigrateWithStateDefinitionsMissingSingleton() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.STATE_DEFINITIONS, SchemaApplicationType.MIGRATION));

        final var stateKey = "KEY";
        var stateDefinitionSingleton = new StateDefinition<>(stateKey, mockCodec, mockCodec, 123, false, true, false);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(
                        serviceName,
                        mirrorNodeState,
                        previousVersion,
                        config,
                        config,
                        new HashMap<>(),
                        startupNetworks));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }

    @Test
    void testMigrateWithStateDefinitionsMissingStateKeyQueue() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.STATE_DEFINITIONS, SchemaApplicationType.MIGRATION));

        final var stateKey = "KEY_QUEUE";
        var stateDefinitionSingleton = new StateDefinition<>(stateKey, mockCodec, mockCodec, 123, false, false, true);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(
                        serviceName,
                        mirrorNodeState,
                        previousVersion,
                        config,
                        config,
                        new HashMap<>(),
                        startupNetworks));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }

    @Test
    void testMigrateWithStateDefinitionsMissingStateKey() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.STATE_DEFINITIONS, SchemaApplicationType.MIGRATION));

        final var stateKey = "STATE_KEY";
        var stateDefinitionSingleton = new StateDefinition<>(stateKey, mockCodec, mockCodec, 123, false, false, false);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(
                        serviceName,
                        mirrorNodeState,
                        previousVersion,
                        config,
                        config,
                        new HashMap<>(),
                        startupNetworks));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }

    @Test
    void testMigrateWithRestartApplication() {
        when(mirrorNodeState.getWritableStates(serviceName)).thenReturn(writableStates);
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);
        when(schemaApplications.computeApplications(any(), any(), any(), any()))
                .thenReturn(EnumSet.of(SchemaApplicationType.RESTART));

        schemaRegistry.register(schema);
        schemaRegistry.migrate(
                serviceName, mirrorNodeState, previousVersion, config, config, new HashMap<>(), startupNetworks);

        verify(schema).restart(any());
        verify(writableStates, times(1)).commit();
    }

    @Test
    void testNewMigrationContext() {
        MigrationContext context = schemaRegistry.newMigrationContext(
                previousVersion, readableStates, writableStates, config, config, EMPTY_MAP, startupNetworks);

        assertThat(context).satisfies(c -> {
            assertDoesNotThrow(() -> c.copyAndReleaseOnDiskState(""));
            assertThat(c.roundNumber()).isZero();
            assertThat(c.startupNetworks()).isEqualTo(startupNetworks);
            assertThat(c.previousVersion()).isEqualTo(previousVersion);
            assertThat(c.previousStates()).isEqualTo(readableStates);
            assertThat(c.newStates()).isEqualTo(writableStates);
            assertThat(c.appConfig()).isEqualTo(config);
            assertThat(c.platformConfig()).isEqualTo(config);
            assertThat(c.sharedValues()).isEqualTo(EMPTY_MAP);
        });
    }
}
