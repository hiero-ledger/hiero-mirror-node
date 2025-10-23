// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFOS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableStates;
import java.util.Set;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaRegistryImplTest {

    private static final String UNSUPPORTED_STATE_KEY_MESSAGE = "Unsupported state key: ";
    private final String serviceName = "testService";

    @Mock
    private MirrorNodeState mirrorNodeState;

    @Mock
    private Schema schema;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private Codec<String> mockCodec;

    private Configuration config;
    private SchemaRegistryImpl schemaRegistry;

    @Mock
    private StateRegistry stateRegistry;

    @BeforeEach
    void initialize() {
        schemaRegistry = new SchemaRegistryImpl(serviceName, stateRegistry);
        config = new ConfigProviderImpl().getConfiguration();
    }

    @Test
    void testMigrateWithSingleSchema() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);

        schemaRegistry.register(schema);

        schemaRegistry.migrate(serviceName, mirrorNodeState, config);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
    }

    @Test
    void testMigrateWithStateDefinitions() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);

        var stateDefinitionSingleton = new StateDefinition<>(
                V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID,
                STAKING_NETWORK_REWARDS_KEY,
                mockCodec,
                mockCodec,
                123,
                false,
                true,
                false);
        var stateDefinitionQueue = new StateDefinition<>(
                V0490TokenSchema.STAKING_INFOS_STATE_ID,
                STAKING_INFOS_KEY,
                mockCodec,
                mockCodec,
                123,
                false,
                false,
                true);
        var stateDefinition = new StateDefinition<>(
                AccountReadableKVState.STATE_ID,
                AccountReadableKVState.KEY,
                mockCodec,
                mockCodec,
                123,
                true,
                false,
                false);

        when(schema.statesToCreate(config))
                .thenReturn(Set.of(stateDefinitionSingleton, stateDefinitionQueue, stateDefinition));

        schemaRegistry.register(schema);
        schemaRegistry.migrate(serviceName, mirrorNodeState, config);
        verify(mirrorNodeState, times(1)).getReadableStates(serviceName);
        verify(mirrorNodeState, times(1)).addService(any(), any());
    }

    @Test
    void testMigrateWithStateDefinitionsMissingSingleton() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);

        final var stateId = 1;
        final var stateKey = "KEY";
        var stateDefinitionSingleton =
                new StateDefinition<>(stateId, stateKey, mockCodec, mockCodec, 123, false, true, false);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(serviceName, mirrorNodeState, config));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }

    @Test
    void testMigrateWithStateDefinitionsMissingStateKeyQueue() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);

        final var stateId = 1;
        final var stateKey = "KEY_QUEUE";
        var stateDefinitionSingleton =
                new StateDefinition<>(stateId, stateKey, mockCodec, mockCodec, 123, false, false, true);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(serviceName, mirrorNodeState, config));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }

    @Test
    void testMigrateWithStateDefinitionsMissingStateKey() {
        when(mirrorNodeState.getReadableStates(serviceName)).thenReturn(readableStates);

        final var stateId = 1;
        final var stateKey = "STATE_KEY";
        var stateDefinitionSingleton =
                new StateDefinition<>(stateId, stateKey, mockCodec, mockCodec, 123, false, false, false);

        when(schema.statesToCreate(config)).thenReturn(Set.of(stateDefinitionSingleton));
        when(stateRegistry.lookup(serviceName, stateDefinitionSingleton))
                .thenThrow(new UnsupportedOperationException(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey));
        schemaRegistry.register(schema);
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> schemaRegistry.migrate(serviceName, mirrorNodeState, config));
        assertThat(exception.getMessage()).isEqualTo(UNSUPPORTED_STATE_KEY_MESSAGE + stateKey);
    }
}
